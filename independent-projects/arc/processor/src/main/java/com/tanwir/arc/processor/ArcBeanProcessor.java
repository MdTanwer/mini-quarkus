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
    "com.tanwir.arc.Dependent"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class ArcBeanProcessor extends AbstractProcessor {

    private static final String GENERATED_PACKAGE = "com.tanwir.arc.generated";
    private static final String GENERATED_SERVICE = "META-INF/services/com.tanwir.arc.GeneratedBeanRegistrar";

    private final Map<String, BeanDefinition> beans = new TreeMap<>();
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

        if (!roundEnv.processingOver() || generated || beans.isEmpty()) {
            return false;
        }

        generated = true;
        generateSources();
        return false;
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
        
        // Generate proxies for @ApplicationScoped beans
        generateProxies();
        
        generateRegistrarSource(registrarSimpleName);
        generateServiceDescriptor(registrarQualifiedName);
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
                    // For @ApplicationScoped beans, register the proxy class
                    String beanClassName = bean.scope == Scope.APPLICATION ? 
                        (bean.qualifiedName.substring(0, bean.qualifiedName.lastIndexOf('.')) + "." + 
                         bean.qualifiedName.substring(bean.qualifiedName.lastIndexOf('.') + 1) + "_Proxy") :
                        bean.qualifiedName;
                    
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

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
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
