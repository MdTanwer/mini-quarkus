package com.tanwir.arc.processor;

import com.tanwir.arc.Inject;
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
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("com.tanwir.arc.Singleton")
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
        for (Element element : roundEnv.getElementsAnnotatedWith(Singleton.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                error(element, "@Singleton can only be used on classes");
                continue;
            }
            TypeElement beanType = (TypeElement) element;
            BeanDefinition bean = BeanDefinition.create(beanType, processingEnv);
            if (bean != null) {
                beans.put(bean.qualifiedName, bean);
            }
        }

        if (!roundEnv.processingOver() || generated || beans.isEmpty()) {
            return false;
        }

        generated = true;
        generateSources();
        return false;
    }

    private void generateSources() {
        String registrarSimpleName = "ArcBeanRegistrar_" + Integer.toHexString(beans.keySet().toString().hashCode());
        String registrarQualifiedName = GENERATED_PACKAGE + "." + registrarSimpleName;
        generateRegistrarSource(registrarSimpleName);
        generateServiceDescriptor(registrarQualifiedName);
    }

    private void generateRegistrarSource(String registrarSimpleName) {
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject file = filer.createSourceFile(GENERATED_PACKAGE + "." + registrarSimpleName);
            try (Writer writer = file.openWriter()) {
                writer.write("package " + GENERATED_PACKAGE + ";\n\n");
                writer.write("public final class " + registrarSimpleName
                        + " implements com.tanwir.arc.GeneratedBeanRegistrar {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(com.tanwir.arc.BeanRegistrar beanRegistrar) {\n");
                for (BeanDefinition bean : beans.values()) {
                    writer.write("        beanRegistrar.register(" + bean.qualifiedName + ".class, container -> ");
                    writer.write("new " + bean.qualifiedName + "(");
                    for (int i = 0; i < bean.constructorParameters.size(); i++) {
                        if (i > 0) {
                            writer.write(", ");
                        }
                        writer.write("container.instance(" + bean.constructorParameters.get(i) + ".class).get()");
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
        private final List<String> constructorParameters;

        private BeanDefinition(String qualifiedName, List<String> constructorParameters) {
            this.qualifiedName = qualifiedName;
            this.constructorParameters = constructorParameters;
        }

        private static BeanDefinition create(TypeElement beanType, ProcessingEnvironment processingEnv) {
            if (!beanType.getModifiers().contains(Modifier.PUBLIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Singleton beans must be public in the first ArC prototype", beanType);
                return null;
            }
            if (beanType.getModifiers().contains(Modifier.ABSTRACT)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Singleton beans cannot be abstract", beanType);
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
                            "Injection constructors must be public in the first ArC prototype", constructor);
                    return null;
                }
                for (VariableElement parameter : constructor.getParameters()) {
                    parameters.add(processingEnv.getTypeUtils().erasure(parameter.asType()).toString());
                }
            }

            return new BeanDefinition(beanType.getQualifiedName().toString(), parameters);
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
