package com.tanwir.arc.processor;

import com.tanwir.arc.ApplicationScoped;
import com.tanwir.arc.Dependent;
import com.tanwir.arc.Inject;
import com.tanwir.arc.PostConstruct;
import com.tanwir.arc.PreDestroy;
import com.tanwir.arc.RequestScoped;
import com.tanwir.arc.Scope;
import com.tanwir.arc.Singleton;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes({
    "com.tanwir.arc.Singleton",
    "com.tanwir.arc.ApplicationScoped",
    "com.tanwir.arc.RequestScoped",
    "com.tanwir.arc.Dependent",
    // @ConsumeEvent is optional — detected by FQN to avoid circular dep on mutiny module
    "com.tanwir.mutiny.ConsumeEvent",
    // @MiniEntity is optional — detected by FQN to avoid circular dep on panache module
    "com.tanwir.panache.MiniEntity",
    "jakarta.persistence.Entity",
    // Interceptor meta-annotations — trigger when user-defined @Interceptor classes are compiled
    "com.tanwir.arc.interceptor.Interceptor",
    "com.tanwir.arc.interceptor.Logged",
    "com.tanwir.arc.interceptor.Timed"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class ArcBeanProcessor extends AbstractProcessor {

    private static final String GENERATED_PACKAGE = "com.tanwir.arc.generated";
    private static final String GENERATED_SERVICE = "META-INF/services/com.tanwir.arc.GeneratedBeanRegistrar";
    private static final String CONSUME_EVENT_FQN = "com.tanwir.mutiny.ConsumeEvent";
    private static final String EVENT_CONSUMER_SERVICE =
            "META-INF/services/com.tanwir.mutiny.MiniEventConsumerRegistrar";
    private static final String MINI_ENTITY_FQN = "com.tanwir.panache.MiniEntity";
    private static final String ENTITY_INDEX_RESOURCE = "META-INF/mini-entity-classes.txt";
    private static final String JPA_ENTITY_FQN = "jakarta.persistence.Entity";
    private static final String JPA_ENTITY_INDEX_RESOURCE = "META-INF/jpa-entity-classes.txt";

    // ---- Interceptor / AOP constants ----
    private static final String INTERCEPTOR_BINDING_FQN = "com.tanwir.arc.interceptor.InterceptorBinding";
    private static final String INTERCEPTOR_FQN        = "com.tanwir.arc.interceptor.Interceptor";
    private static final String AROUND_INVOKE_FQN      = "com.tanwir.arc.interceptor.AroundInvoke";
    private static final String MINI_INTERCEPTORS_RES  = "META-INF/mini-interceptors.txt";

    private final Map<String, BeanDefinition> beans = new TreeMap<>();
    /** All @ConsumeEvent methods discovered across all bean classes. */
    private final List<EventConsumerDefinition> eventConsumers = new ArrayList<>();
    /** All @MiniEntity class names — written to META-INF/mini-entity-classes.txt for the deployment processor. */
    private final List<String> entityClassNames = new ArrayList<>();
    private final List<String> jpaEntityClassNames = new ArrayList<>();
    /**
     * Binding annotation FQN → interceptor class FQN.
     * Populated from {@code META-INF/mini-interceptors.txt} (built-ins) and user-defined
     * {@code @Interceptor} classes discovered in the compilation unit.
     */
    private final Map<String, String> bindingToInterceptor = new java.util.LinkedHashMap<>();
    /**
     * Bean FQN → list of interceptor method descriptors.
     * Populated in {@link #buildInterceptorData()} during {@link #generateSources()}.
     */
    private final Map<String, List<InterceptorMethodInfo>> beanInterceptorMethods = new java.util.HashMap<>();
    private boolean generated;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Process all scope annotations
        for (Element element : roundEnv.getElementsAnnotatedWith(Singleton.class)) {
            processScopeElement(element, Scope.SINGLETON);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ApplicationScoped.class)) {
            processScopeElement(element, Scope.APPLICATION);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(RequestScoped.class)) {
            processScopeElement(element, Scope.REQUEST);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Dependent.class)) {
            processScopeElement(element, Scope.DEPENDENT);
        }

        // Scan all root elements for @ConsumeEvent, @MiniEntity, and @Interceptor.
        for (Element rootElement : roundEnv.getRootElements()) {
            if (rootElement.getKind() == ElementKind.CLASS) {
                for (Element enclosed : rootElement.getEnclosedElements()) {
                    if (enclosed.getKind() == ElementKind.METHOD) {
                        processConsumeEventMethod((ExecutableElement) enclosed, (TypeElement) rootElement);
                    }
                }
                processMiniEntityClass((TypeElement) rootElement);
                processJpaEntityClass((TypeElement) rootElement);
                // Discover user-defined @Interceptor classes in this compilation unit.
                // Mirrors how Quarkus's ArcProcessor.findInterceptors() uses the Jandex index.
                scanUserInterceptorClass((TypeElement) rootElement);
            }
            if (rootElement.getKind().name().equals("RECORD")) {
                processMiniEntityClass((TypeElement) rootElement);
                processJpaEntityClass((TypeElement) rootElement);
            }
        }

        if (!roundEnv.processingOver() || generated || beans.isEmpty()) {
            return false;
        }

        generated = true;
        generateSources();
        return false;
    }

    /**
     * Detects {@link com.tanwir.panache.MiniEntity}-annotated classes (by FQN) and records
     * their fully-qualified names. The names are later written to
     * {@code META-INF/mini-entity-classes.txt} so the deployment-time
     * {@code PanacheDeploymentProcessor} can discover them without classpath scanning.
     *
     * <p>Mirrors how Quarkus's {@code HibernateOrmProcessor} uses the Jandex index to find
     * {@code @Entity}-annotated classes. In mini-quarkus we generate this index during
     * annotation processing (compile time) and read it at startup (deployment time).
     */
    private void processMiniEntityClass(TypeElement classElement) {
        for (javax.lang.model.element.AnnotationMirror am : classElement.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(MINI_ENTITY_FQN)) {
                String fqn = classElement.getQualifiedName().toString();
                if (!entityClassNames.contains(fqn)) {
                    entityClassNames.add(fqn);
                }
                return;
            }
        }
    }

    private void processJpaEntityClass(TypeElement classElement) {
        for (javax.lang.model.element.AnnotationMirror am : classElement.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(JPA_ENTITY_FQN)) {
                String fqn = classElement.getQualifiedName().toString();
                if (!jpaEntityClassNames.contains(fqn)) {
                    jpaEntityClassNames.add(fqn);
                }
                return;
            }
        }
    }

    private void processConsumeEventMethod(ExecutableElement method, TypeElement beanClass) {
        for (javax.lang.model.element.AnnotationMirror am : method.getAnnotationMirrors()) {
            if (!am.getAnnotationType().toString().equals(CONSUME_EVENT_FQN)) {
                continue;
            }
            // Extract address (value element) — default to bean FQN if empty
            String address = beanClass.getQualifiedName().toString();
            boolean blocking = false;
            for (Map.Entry<? extends javax.lang.model.element.ExecutableElement,
                    ? extends javax.lang.model.element.AnnotationValue> entry :
                    am.getElementValues().entrySet()) {
                String elementName = entry.getKey().getSimpleName().toString();
                if ("value".equals(elementName)) {
                    String val = entry.getValue().getValue().toString();
                    if (!val.isEmpty()) {
                        address = val;
                    }
                } else if ("blocking".equals(elementName)) {
                    blocking = Boolean.parseBoolean(entry.getValue().getValue().toString());
                }
            }
            // Determine parameter type (first param, or null if none)
            String paramType = null;
            if (!method.getParameters().isEmpty()) {
                paramType = processingEnv.getTypeUtils()
                        .erasure(method.getParameters().get(0).asType()).toString();
            }
            // Determine return type
            String returnType = processingEnv.getTypeUtils()
                    .erasure(method.getReturnType()).toString();
            boolean returnsVoid = method.getReturnType().getKind() == TypeKind.VOID;

            eventConsumers.add(new EventConsumerDefinition(
                    beanClass.getQualifiedName().toString(),
                    method.getSimpleName().toString(),
                    address,
                    paramType,
                    returnType,
                    returnsVoid,
                    blocking));
        }
    }

    private void processScopeElement(Element element, Scope scope) {
        if (element.getKind() != ElementKind.CLASS) {
            error(element, "@" + getScopeAnnotationName(scope) + " can only be used on classes");
            return;
        }
        TypeElement beanType = (TypeElement) element;
        
        // Validate only one scope annotation per class
        validateSingleScope(beanType, scope);
        
        BeanDefinition bean = BeanDefinition.create(beanType, scope, processingEnv);
        if (bean != null) {
            beans.put(bean.qualifiedName, bean);
        }
    }

    private void validateSingleScope(TypeElement beanType, Scope currentScope) {
        int scopeCount = 0;
        if (beanType.getAnnotation(Singleton.class) != null) scopeCount++;
        if (beanType.getAnnotation(ApplicationScoped.class) != null) scopeCount++;
        if (beanType.getAnnotation(RequestScoped.class) != null) scopeCount++;
        if (beanType.getAnnotation(Dependent.class) != null) scopeCount++;
        
        if (scopeCount > 1) {
            error(beanType, "Only one scope annotation is allowed per class. Found multiple scope annotations.");
        }
    }

    private String getScopeAnnotationName(Scope scope) {
        return switch (scope) {
            case SINGLETON -> "Singleton";
            case APPLICATION -> "ApplicationScoped";
            case REQUEST -> "RequestScoped";
            case DEPENDENT -> "Dependent";
        };
    }

    private void generateSources() {
        String registrarSimpleName = "ArcBeanRegistrar_" + Integer.toHexString(beans.keySet().toString().hashCode());
        String registrarQualifiedName = GENERATED_PACKAGE + "." + registrarSimpleName;

        // ---- Phase 8: Interceptor / AOP wiring ----
        // Step 1: load built-in binding→interceptor mappings from META-INF/mini-interceptors.txt
        loadBuiltinInterceptors();
        // Step 2: scan each known CDI bean for methods with interceptor binding annotations
        buildInterceptorData();
        // Step 3: generate _Subclass source files for beans that need AOP wrapping
        if (!beanInterceptorMethods.isEmpty()) {
            generateInterceptorSubclasses();
        }

        // Generate proxies for @ApplicationScoped beans (non-intercepted only)
        generateProxies();

        generateRegistrarSource(registrarSimpleName);
        generateServiceDescriptor(registrarQualifiedName);

        // Generate EventBus consumer registrar only when @ConsumeEvent methods were found.
        // Mirrors Quarkus's VertxProcessor generating EventConsumerInvoker classes.
        if (!eventConsumers.isEmpty()) {
            generateEventConsumerRegistrar();
        }

        // Write compile-time entity index for the Panache deployment processor.
        if (!entityClassNames.isEmpty()) {
            generateEntityIndex();
        }
        if (!jpaEntityClassNames.isEmpty()) {
            generateJpaEntityIndex();
        }
    }

    /**
     * Writes the list of discovered {@code @MiniEntity} class names to
     * {@code META-INF/mini-entity-classes.txt}. One FQN per line.
     *
     * <p>Consumed at startup by {@code PanacheDeploymentProcessor.discoverEntities()}.
     */
    private void generateEntityIndex() {
        writeClassListResource(ENTITY_INDEX_RESOURCE, entityClassNames);
    }

    private void generateJpaEntityIndex() {
        writeClassListResource(JPA_ENTITY_INDEX_RESOURCE, jpaEntityClassNames);
    }

    private void writeClassListResource(String resource, List<String> classNames) {
        Filer filer = processingEnv.getFiler();
        try {
            FileObject file = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "", resource);
            try (java.io.Writer writer = file.openWriter()) {
                for (String fqn : classNames) {
                    writer.write(fqn);
                    writer.write("\n");
                }
            }
        } catch (java.io.IOException e) {
            processingEnv.getMessager().printMessage(
                    javax.tools.Diagnostic.Kind.WARNING,
                    "Could not write " + resource + ": " + e.getMessage());
        }
    }

    private void generateRegistrarSource(String registrarSimpleName) {
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject file = filer.createSourceFile(GENERATED_PACKAGE + "." + registrarSimpleName);
            try (Writer writer = file.openWriter()) {
                writer.write("package " + GENERATED_PACKAGE + ";\n\n");
                writer.write("import com.tanwir.arc.BeanDescriptor;\n");
                writer.write("import com.tanwir.arc.Scope;\n");
                writer.write("import java.util.Set;\n\n");
                writer.write("public final class " + registrarSimpleName
                        + " implements com.tanwir.arc.GeneratedBeanRegistrar {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(com.tanwir.arc.BeanRegistrar beanRegistrar) {\n");
                for (BeanDefinition bean : beans.values()) {
                    // Determine instantiation class:
                    //   1. Intercepted bean  → use _Subclass (woven interceptor chain)
                    //   2. @ApplicationScoped → use _Proxy  (scoping proxy)
                    //   3. Other             → use original class
                    // Note: intercepted @ApplicationScoped uses _Subclass only (limitation noted).
                    String pkg = bean.qualifiedName.substring(0, bean.qualifiedName.lastIndexOf('.'));
                    String simple = bean.qualifiedName.substring(bean.qualifiedName.lastIndexOf('.') + 1);
                    String beanClassName;
                    if (beanInterceptorMethods.containsKey(bean.qualifiedName)) {
                        beanClassName = pkg + "." + simple + "_Subclass";
                    } else if (bean.scope == Scope.APPLICATION) {
                        beanClassName = pkg + "." + simple + "_Proxy";
                    } else {
                        beanClassName = bean.qualifiedName;
                    }
                    
                    writer.write("        beanRegistrar.register(new BeanDescriptor<>(\n");
                    writer.write("            " + bean.qualifiedName + ".class,\n"); // Always use original bean class as type
                    writer.write("            Scope." + bean.scope.name() + ",\n");
                    writer.write("            container -> {\n");
                    writer.write("                " + beanClassName + " instance = new " + beanClassName + "(");
                    for (int i = 0; i < bean.constructorParameters.size(); i++) {
                        if (i > 0) {
                            writer.write(", ");
                        }
                        writer.write("container.instance(" + bean.constructorParameters.get(i) + ".class).get()");
                    }
                    writer.write(");\n");
                    // Inject configuration if the bean has @ConfigProperty fields
                    writer.write("                try {\n");
                    writer.write("                    Class<?> configLoaderClass = Class.forName(\"" + bean.qualifiedName + "ConfigLoader\");\n");
                    writer.write("                    Object configLoader = configLoaderClass.getDeclaredConstructor().newInstance();\n");
                    writer.write("                    configLoaderClass.getMethod(\"inject\", " + bean.qualifiedName + ".class).invoke(configLoader, instance);\n");
                    writer.write("                } catch (Exception e) {\n");
                    writer.write("                    // No config loader found or injection failed, that's okay\n");
                    writer.write("                }\n");
                    writer.write("                return instance;\n");
                    writer.write("            },\n");
                    writer.write("            " + (bean.postConstructMethod != null ? "\"" + bean.postConstructMethod + "\"" : "null") + ",\n");
                    writer.write("            " + (bean.preDestroyMethod != null ? "\"" + bean.preDestroyMethod + "\"" : "null") + ",\n");
                    writer.write("            Set.of(");
                    if (bean.qualifiers.isEmpty()) {
                        writer.write(")");
                    } else {
                        for (int i = 0; i < bean.qualifiers.size(); i++) {
                            if (i > 0) writer.write(", ");
                            writer.write(bean.qualifiers.get(i) + ".class");
                        }
                        writer.write(")");
                    }
                    writer.write("));\n");
                }
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate ArC bean registrar", e);
        }
    }

    private void generateProxies() {
        for (BeanDefinition bean : beans.values()) {
            if (bean.scope == Scope.APPLICATION) {
                generateProxy(bean);
            }
        }
    }

    private void generateProxy(BeanDefinition bean) {
        Filer filer = processingEnv.getFiler();
        String proxySimpleName = bean.qualifiedName.substring(bean.qualifiedName.lastIndexOf('.') + 1) + "_Proxy";
        String proxyPackage = bean.qualifiedName.substring(0, bean.qualifiedName.lastIndexOf('.'));
        String proxyQualifiedName = proxyPackage + "." + proxySimpleName;
        
        try {
            JavaFileObject file = filer.createSourceFile(proxyQualifiedName);
            try (Writer writer = file.openWriter()) {
                writer.write("package " + proxyPackage + ";\n\n");
                writer.write("// Generated proxy for: @" + bean.scope.name().toLowerCase() + " " + bean.qualifiedName + "\n");
                writer.write("public final class " + proxySimpleName + " extends " + bean.qualifiedName + " {\n");
                writer.write("    public " + proxySimpleName + "(");
                for (int i = 0; i < bean.constructorParameters.size(); i++) {
                    if (i > 0) {
                        writer.write(", ");
                    }
                    writer.write(bean.constructorParameters.get(i) + " arg" + i);
                }
                writer.write(") {\n");
                writer.write("        super(");
                for (int i = 0; i < bean.constructorParameters.size(); i++) {
                    if (i > 0) {
                        writer.write(", ");
                    }
                    writer.write("arg" + i);
                }
                writer.write(");\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate proxy for " + bean.qualifiedName, e);
        }
    }

    private void generateServiceDescriptor(String registrarQualifiedName) {
        Filer filer = processingEnv.getFiler();
        try {
            FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", GENERATED_SERVICE);
            try (Writer writer = file.openWriter()) {
                writer.write(registrarQualifiedName);
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate ArC service descriptor", e);
        }
    }

    // -------------------------------------------------------------------------
    // @ConsumeEvent — EventBus consumer registrar generation
    // -------------------------------------------------------------------------

    /**
     * Generates {@code com.tanwir.arc.generated.GeneratedEventConsumerRegistrar} and its
     * service descriptor. Mirrors what Quarkus's {@code VertxProcessor} does when it finds
     * {@code @ConsumeEvent} methods during build-time augmentation.
     */
    private void generateEventConsumerRegistrar() {
        String simpleName = "GeneratedEventConsumerRegistrar";
        String qualifiedName = GENERATED_PACKAGE + "." + simpleName;
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject file = filer.createSourceFile(qualifiedName);
            try (Writer writer = file.openWriter()) {
                writer.write("package " + GENERATED_PACKAGE + ";\n\n");
                writer.write("// Generated by ArcBeanProcessor — do not edit\n");
                writer.write("public final class " + simpleName
                        + " implements com.tanwir.mutiny.MiniEventConsumerRegistrar {\n\n");
                writer.write("    @Override\n");
                writer.write("    public void register(io.vertx.core.Vertx vertx, com.tanwir.arc.ArcContainer container) {\n");

                for (EventConsumerDefinition ec : eventConsumers) {
                    writer.write("        // @ConsumeEvent(\"" + ec.address() + "\") -> "
                            + ec.beanClass() + "#" + ec.methodName() + "\n");
                    if (ec.blocking()) {
                        generateBlockingConsumer(writer, ec);
                    } else {
                        generateNonBlockingConsumer(writer, ec);
                    }
                }

                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate EventConsumer registrar", e);
        }

        // Service descriptor for MiniEventConsumerRegistrar
        try {
            FileObject svc = filer.createResource(StandardLocation.CLASS_OUTPUT, "", EVENT_CONSUMER_SERVICE);
            try (Writer writer = svc.openWriter()) {
                writer.write(qualifiedName + "\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate EventConsumer service descriptor", e);
        }
    }

    private void generateNonBlockingConsumer(Writer w, EventConsumerDefinition ec) throws IOException {
        w.write("        vertx.eventBus().consumer(\"" + ec.address() + "\", msg -> {\n");
        w.write("            " + ec.beanClass() + " bean = container.instance(" + ec.beanClass() + ".class).get();\n");
        if (ec.returnsVoid()) {
            if (ec.paramType() != null) {
                w.write("            bean." + ec.methodName() + "((" + ec.paramType() + ") msg.body());\n");
            } else {
                w.write("            bean." + ec.methodName() + "();\n");
            }
        } else {
            if (ec.paramType() != null) {
                w.write("            Object result = bean." + ec.methodName() + "((" + ec.paramType() + ") msg.body());\n");
            } else {
                w.write("            Object result = bean." + ec.methodName() + "();\n");
            }
            // Handle Uni<T> return — subscribe and reply when resolved
            w.write("            if (result instanceof io.smallrye.mutiny.Uni<?> uni) {\n");
            w.write("                uni.subscribe().with(r -> { if (r != null) msg.reply(r); }, t -> msg.fail(500, t.getMessage()));\n");
            w.write("            } else if (result != null) {\n");
            w.write("                msg.reply(result);\n");
            w.write("            }\n");
        }
        w.write("        });\n");
    }

    private void generateBlockingConsumer(Writer w, EventConsumerDefinition ec) throws IOException {
        // blocking = true -> run handler on a Vert.x worker thread, same as real Quarkus
        w.write("        vertx.eventBus().consumer(\"" + ec.address() + "\", msg -> {\n");
        w.write("            vertx.executeBlocking(() -> {\n");
        w.write("                " + ec.beanClass() + " bean = container.instance(" + ec.beanClass() + ".class).get();\n");
        if (ec.paramType() != null) {
            w.write("                return (" + (ec.returnsVoid() ? "Void" : "Object") + ") bean."
                    + ec.methodName() + "((" + ec.paramType() + ") msg.body());\n");
        } else {
            w.write("                return (" + (ec.returnsVoid() ? "Void" : "Object") + ") bean."
                    + ec.methodName() + "();\n");
        }
        w.write("            }).onSuccess(r -> { if (r != null) msg.reply(r); })\n");
        w.write("              .onFailure(t -> msg.fail(500, t.getMessage()));\n");
        w.write("        });\n");
    }

    // -------------------------------------------------------------------------
    // Interceptor / AOP — discovery, data building, subclass generation
    // -------------------------------------------------------------------------

    /**
     * Reads {@code META-INF/mini-interceptors.txt} from the processor classpath to populate
     * the built-in {@link #bindingToInterceptor} map.
     *
     * <p>Each non-comment line has the form {@code bindingFQN=interceptorFQN}.
     * This mirrors Quarkus using Jandex to find {@code @Interceptor}-annotated classes
     * bundled in extension JARs.
     */
    private void loadBuiltinInterceptors() {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(MINI_INTERCEPTORS_RES)) {
            if (is == null) return;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        bindingToInterceptor.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
            }
        } catch (java.io.IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "Could not load " + MINI_INTERCEPTORS_RES + ": " + e.getMessage());
        }
    }

    /**
     * Scans a user-defined class for {@code @Interceptor} (by FQN). If found, reads the
     * interceptor's own binding annotations and adds entries to {@link #bindingToInterceptor}.
     *
     * <p>This enables user-defined interceptors (defined in the application compilation unit,
     * not in {@code arc/runtime}) to be discovered — mirrors the real Quarkus Jandex scan.
     */
    private void scanUserInterceptorClass(TypeElement classElement) {
        boolean isInterceptor = false;
        List<String> bindingsOnInterceptor = new ArrayList<>();

        for (javax.lang.model.element.AnnotationMirror am : classElement.getAnnotationMirrors()) {
            String amFqn = am.getAnnotationType().toString();
            if (amFqn.equals(INTERCEPTOR_FQN)) {
                isInterceptor = true;
            } else {
                // Check if this annotation is itself annotated with @InterceptorBinding
                TypeElement amType = processingEnv.getElementUtils().getTypeElement(amFqn);
                if (amType != null && hasMetaAnnotation(amType, INTERCEPTOR_BINDING_FQN)) {
                    bindingsOnInterceptor.add(amFqn);
                }
            }
        }
        if (isInterceptor) {
            for (String binding : bindingsOnInterceptor) {
                bindingToInterceptor.put(binding, classElement.getQualifiedName().toString());
            }
        }
    }

    /**
     * For each known CDI bean, scans its public methods for interceptor binding annotations
     * and builds the {@link #beanInterceptorMethods} map.
     *
     * <p>Mirrors {@code io.quarkus.arc.deployment.ArcProcessor#findInterceptorBindings} which
     * uses the Jandex index to resolve binding annotation usage on bean methods.
     */
    private void buildInterceptorData() {
        for (BeanDefinition bean : beans.values()) {
            TypeElement beanType = processingEnv.getElementUtils().getTypeElement(bean.qualifiedName);
            if (beanType == null) continue;
            List<InterceptorMethodInfo> methods = findInterceptorMethods(beanType);
            if (!methods.isEmpty()) {
                beanInterceptorMethods.put(bean.qualifiedName, methods);
            }
        }
    }

    /**
     * Returns interceptor method descriptors for all public methods of {@code beanType}
     * that carry at least one interceptor binding annotation.
     */
    private List<InterceptorMethodInfo> findInterceptorMethods(TypeElement beanType) {
        List<InterceptorMethodInfo> result = new ArrayList<>();
        for (ExecutableElement method : ElementFilter.methodsIn(beanType.getEnclosedElements())) {
            if (!method.getModifiers().contains(Modifier.PUBLIC)
                    || method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            List<String> interceptors = findInterceptorsForMethod(method);
            if (!interceptors.isEmpty()) {
                String returnFqn = processingEnv.getTypeUtils().erasure(method.getReturnType()).toString();
                boolean returnsVoid = method.getReturnType().getKind() == TypeKind.VOID;
                List<String> paramFqns = new ArrayList<>();
                for (VariableElement param : method.getParameters()) {
                    paramFqns.add(processingEnv.getTypeUtils().erasure(param.asType()).toString());
                }
                result.add(new InterceptorMethodInfo(
                        method.getSimpleName().toString(),
                        returnFqn, returnsVoid, paramFqns, interceptors));
            }
        }
        return result;
    }

    /**
     * Returns the ordered list of interceptor class FQNs that should be applied to {@code method}.
     * Checks both the method itself and the enclosing class for binding annotations.
     */
    private List<String> findInterceptorsForMethod(ExecutableElement method) {
        List<String> interceptors = new ArrayList<>();
        // Method-level bindings
        collectBindings(method, interceptors);
        // Class-level bindings (apply to all methods of the class)
        collectBindings((TypeElement) method.getEnclosingElement(), interceptors);
        return interceptors;
    }

    /** Appends interceptor FQNs for each interceptor binding annotation on {@code element}. */
    private void collectBindings(javax.lang.model.element.Element element, List<String> interceptors) {
        for (javax.lang.model.element.AnnotationMirror am : element.getAnnotationMirrors()) {
            String amFqn = am.getAnnotationType().toString();
            // Check if this annotation type is a binding (has @InterceptorBinding on it)
            TypeElement amType = processingEnv.getElementUtils().getTypeElement(amFqn);
            if (amType != null && hasMetaAnnotation(amType, INTERCEPTOR_BINDING_FQN)) {
                String interceptorFqn = bindingToInterceptor.get(amFqn);
                if (interceptorFqn != null && !interceptors.contains(interceptorFqn)) {
                    interceptors.add(interceptorFqn);
                }
            }
        }
    }

    /** Returns true if {@code type} has an annotation with the given FQN on it. */
    private boolean hasMetaAnnotation(TypeElement type, String metaFqn) {
        for (javax.lang.model.element.AnnotationMirror am : type.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(metaFqn)) return true;
        }
        return false;
    }

    /**
     * Generates a {@code _Subclass} source file for each bean that has interceptor-bound methods.
     *
     * <p>The generated subclass:
     * <ul>
     *   <li>Extends the original bean class (not the proxy — see limitation note in the registrar)</li>
     *   <li>Overrides each interceptor-bound method</li>
     *   <li>Wraps the method body with {@code SimpleInvocationContext.chain(...)} calls,
     *       one per interceptor — building the chain from innermost (target) outward</li>
     * </ul>
     *
     * <p>Mirrors Quarkus's {@code io.quarkus.arc.processor.SubclassGenerator} which generates
     * interceptor subclasses using Gizmo bytecode generation. Mini-quarkus uses source-level
     * generation (the same approach as the bean registrar and route registrar).
     */
    private void generateInterceptorSubclasses() {
        for (Map.Entry<String, List<InterceptorMethodInfo>> entry : beanInterceptorMethods.entrySet()) {
            String beanFqn = entry.getKey();
            BeanDefinition bean = beans.get(beanFqn);
            if (bean == null) continue;
            generateInterceptorSubclass(bean, entry.getValue());
        }
    }

    private void generateInterceptorSubclass(BeanDefinition bean, List<InterceptorMethodInfo> methods) {
        String pkg = bean.qualifiedName.substring(0, bean.qualifiedName.lastIndexOf('.'));
        String simple = bean.qualifiedName.substring(bean.qualifiedName.lastIndexOf('.') + 1);
        String subclassSimple = simple + "_Subclass";
        String subclassFqn = pkg + "." + subclassSimple;

        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject file = filer.createSourceFile(subclassFqn);
            try (Writer w = file.openWriter()) {
                w.write("package " + pkg + ";\n\n");
                w.write("// Generated by ArcBeanProcessor — CDI interceptor subclass for " + bean.qualifiedName + "\n");
                w.write("// Mirrors what Quarkus ArC SubclassGenerator produces via Gizmo bytecode.\n");
                w.write("public final class " + subclassSimple + " extends " + bean.qualifiedName + " {\n\n");

                // Constructor — delegates to super with the same parameters
                w.write("    public " + subclassSimple + "(");
                for (int i = 0; i < bean.constructorParameters.size(); i++) {
                    if (i > 0) w.write(", ");
                    w.write(bean.constructorParameters.get(i) + " _arg" + i);
                }
                w.write(") {\n        super(");
                for (int i = 0; i < bean.constructorParameters.size(); i++) {
                    if (i > 0) w.write(", ");
                    w.write("_arg" + i);
                }
                w.write(");\n    }\n\n");

                // Overriding methods with interceptor chains
                for (InterceptorMethodInfo m : methods) {
                    writeInterceptedMethod(w, m, bean.qualifiedName);
                }

                w.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate interceptor subclass for " + bean.qualifiedName, e);
        }
    }

    /**
     * Writes one interceptor-woven method override to the output writer.
     *
     * <p>Generated pattern (innermost = target method, outermost = first interceptor):
     * <pre>
     *   InvocationContext _ctx = new SimpleInvocationContext(this, "methodName", args, () -> super.method(args));
     *   _ctx = SimpleInvocationContext.chain(_ctx, new Interceptor2()); // applied last (outer)
     *   _ctx = SimpleInvocationContext.chain(_ctx, new Interceptor1()); // applied first (inner)
     *   return (ReturnType) _ctx.proceed();
     * </pre>
     */
    private void writeInterceptedMethod(Writer w, InterceptorMethodInfo m, String beanFqn) throws IOException {
        String returnType = m.returnsVoid ? "void" : m.returnTypeFqn;
        w.write("    @Override\n");
        w.write("    public " + returnType + " " + m.methodName + "(");
        for (int i = 0; i < m.paramTypeFqns.size(); i++) {
            if (i > 0) w.write(", ");
            w.write(m.paramTypeFqns.get(i) + " _p" + i);
        }
        w.write(") {\n");

        // Build args array expression
        String argsExpr;
        if (m.paramTypeFqns.isEmpty()) {
            argsExpr = "new Object[0]";
        } else {
            StringBuilder sb = new StringBuilder("new Object[]{");
            for (int i = 0; i < m.paramTypeFqns.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("_p").append(i);
            }
            sb.append("}");
            argsExpr = sb.toString();
        }

        // Build super call expression
        StringBuilder superCall = new StringBuilder("super." + m.methodName + "(");
        for (int i = 0; i < m.paramTypeFqns.size(); i++) {
            if (i > 0) superCall.append(", ");
            superCall.append("_p").append(i);
        }
        superCall.append(")");

        String proceedLambda = m.returnsVoid
                ? "() -> { " + superCall + "; return null; }"
                : "() -> (Object) " + superCall;

        w.write("        com.tanwir.arc.interceptor.InvocationContext _ctx =\n");
        w.write("            new com.tanwir.arc.interceptor.SimpleInvocationContext(\n");
        w.write("                this, \"" + m.methodName + "\", " + argsExpr + ",\n");
        w.write("                " + proceedLambda + ");\n");

        // Chain interceptors — iterate in reverse so the first interceptor in the list
        // is the outermost wrapper (called first). Each chain() wraps the current _ctx.
        for (int i = m.interceptorFqns.size() - 1; i >= 0; i--) {
            w.write("        _ctx = com.tanwir.arc.interceptor.SimpleInvocationContext.chain(\n");
            w.write("            _ctx, new " + m.interceptorFqns.get(i) + "());\n");
        }

        w.write("        try {\n");
        if (m.returnsVoid) {
            w.write("            _ctx.proceed();\n");
        } else {
            w.write("            return (" + m.returnTypeFqn + ") _ctx.proceed();\n");
        }
        w.write("        } catch (RuntimeException _e) { throw _e; }\n");
        w.write("          catch (Exception _e) { throw new RuntimeException(_e); }\n");
        w.write("    }\n\n");
    }

    /** Record carrying all info needed to generate one intercepted method override. */
    private record InterceptorMethodInfo(
            String methodName,
            String returnTypeFqn,
            boolean returnsVoid,
            List<String> paramTypeFqns,
            List<String> interceptorFqns) {}

    // -------------------------------------------------------------------------

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    /**
     * Metadata for a single {@link com.tanwir.mutiny.ConsumeEvent}-annotated method.
     * Mirrors Quarkus's internal {@code EventConsumerBusinessMethod} model.
     */
    private record EventConsumerDefinition(
            String beanClass,
            String methodName,
            String address,
            String paramType,
            String returnType,
            boolean returnsVoid,
            boolean blocking) {
    }

    private static final class BeanDefinition {

        private final String qualifiedName;
        private final Scope scope;
        private final List<String> constructorParameters;
        private final String postConstructMethod;
        private final String preDestroyMethod;
        private final List<String> qualifiers;

        private BeanDefinition(String qualifiedName, Scope scope, List<String> constructorParameters,
                            String postConstructMethod, String preDestroyMethod, List<String> qualifiers) {
            this.qualifiedName = qualifiedName;
            this.scope = scope;
            this.constructorParameters = constructorParameters;
            this.postConstructMethod = postConstructMethod;
            this.preDestroyMethod = preDestroyMethod;
            this.qualifiers = qualifiers;
        }

        private static BeanDefinition create(TypeElement beanType, Scope scope, ProcessingEnvironment processingEnv) {
            if (!beanType.getModifiers().contains(Modifier.PUBLIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Beans must be public", beanType);
                return null;
            }
            if (beanType.getModifiers().contains(Modifier.ABSTRACT)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Beans cannot be abstract", beanType);
                return null;
            }

            ConstructorSelection selection = selectConstructor(beanType, processingEnv);
            if (!selection.valid()) {
                return null;
            }

            List<String> parameters = new ArrayList<>();
            if (selection.constructor() != null) {
                ExecutableElement constructor = selection.constructor();
                if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Injection constructors must be public", constructor);
                    return null;
                }
                for (VariableElement parameter : constructor.getParameters()) {
                    parameters.add(processingEnv.getTypeUtils().erasure(parameter.asType()).toString());
                }
            }

            // Find @PostConstruct and @PreDestroy methods
            String postConstructMethod = findLifecycleMethod(beanType, PostConstruct.class, processingEnv);
            String preDestroyMethod = findLifecycleMethod(beanType, PreDestroy.class, processingEnv);

            // Find qualifier annotations
            List<String> qualifiers = findQualifiers(beanType, processingEnv);

            // Validate @Dependent beans cannot have @PreDestroy invoked by container
            if (scope == Scope.DEPENDENT && preDestroyMethod != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        "@Dependent beans cannot have @PreDestroy invoked by container automatically. " +
                        "@PreDestroy will only be called when InstanceHandle.close() is invoked.", beanType);
            }

            return new BeanDefinition(beanType.getQualifiedName().toString(), scope, parameters,
                    postConstructMethod, preDestroyMethod, qualifiers);
        }

        private static String findLifecycleMethod(TypeElement beanType, Class<? extends java.lang.annotation.Annotation> annotationClass,
                                              ProcessingEnvironment processingEnv) {
            List<ExecutableElement> methods = ElementFilter.methodsIn(beanType.getEnclosedElements());
            List<ExecutableElement> annotatedMethods = methods.stream()
                    .filter(m -> m.getAnnotation(annotationClass) != null)
                    .toList();

            if (annotatedMethods.isEmpty()) {
                return null;
            }

            if (annotatedMethods.size() > 1) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Only one method can be annotated with @" + annotationClass.getSimpleName(), beanType);
                return null;
            }

            ExecutableElement method = annotatedMethods.get(0);
            
            // Validate method signature: public void methodName()
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@" + annotationClass.getSimpleName() + " method must be public", method);
                return null;
            }
            
            if (!method.getParameters().isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@" + annotationClass.getSimpleName() + " method must have no parameters", method);
                return null;
            }
            
            if (!processingEnv.getTypeUtils().isSameType(method.getReturnType(), 
                    processingEnv.getTypeUtils().getNoType(TypeKind.VOID))) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@" + annotationClass.getSimpleName() + " method must return void", method);
                return null;
            }

            return method.getSimpleName().toString();
        }

        private static List<String> findQualifiers(TypeElement beanType, ProcessingEnvironment processingEnv) {
            List<String> qualifiers = new ArrayList<>();
            for (javax.lang.model.element.AnnotationMirror annotation : beanType.getAnnotationMirrors()) {
                TypeElement annotationElement = (TypeElement) annotation.getAnnotationType().asElement();
                if (annotationElement.getAnnotation(com.tanwir.arc.Qualifier.class) != null) {
                    qualifiers.add(annotationElement.getQualifiedName().toString());
                }
            }
            return qualifiers;
        }

        private static ConstructorSelection selectConstructor(TypeElement beanType, ProcessingEnvironment processingEnv) {
            List<ExecutableElement> constructors = new ArrayList<>(ElementFilter.constructorsIn(beanType.getEnclosedElements()));
            constructors.sort(Comparator.comparing(c -> c.getParameters().size()));
            if (constructors.isEmpty()) {
                return ConstructorSelection.valid(null);
            }

            List<ExecutableElement> injectConstructors = constructors.stream()
                    .filter(c -> c.getAnnotation(Inject.class) != null)
                    .toList();
            if (injectConstructors.size() > 1) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Only one constructor can be annotated with @Inject", beanType);
                return ConstructorSelection.invalid();
            }
            if (injectConstructors.size() == 1) {
                return ConstructorSelection.valid(injectConstructors.get(0));
            }
            if (constructors.size() == 1) {
                return ConstructorSelection.valid(constructors.get(0));
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Bean must declare exactly one constructor or mark one with @Inject", beanType);
            return ConstructorSelection.invalid();
        }

        private record ConstructorSelection(ExecutableElement constructor, boolean valid) {

            private static ConstructorSelection valid(ExecutableElement constructor) {
                return new ConstructorSelection(constructor, true);
            }

            private static ConstructorSelection invalid() {
                return new ConstructorSelection(null, false);
            }
        }
    }
}
