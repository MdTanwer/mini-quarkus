package com.tanwir.resteasy.reactive.server.processor;

import com.tanwir.arc.Singleton;
import com.tanwir.bootstrap.model.MiniApplicationModelConstants;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@SupportedAnnotationTypes("jakarta.ws.rs.GET")
@SupportedOptions(MiniApplicationModelConstants.OPTION_APPLICATION_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class MiniResteasyReactiveProcessor extends AbstractProcessor {

    private static final String GENERATED_PACKAGE = "com.tanwir.resteasy.reactive.generated";
    private static final String GENERATED_SERVICE =
            "META-INF/services/com.tanwir.resteasy.reactive.server.GeneratedRouteRegistrar";

    private final Map<String, RouteDefinition> routes = new TreeMap<>();
    private boolean generated;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GET.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                error(element, "@GET can only be used on methods");
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            RouteDefinition route = RouteDefinition.create(method, processingEnv);
            if (route != null) {
                routes.put(route.operationId, route);
            }
        }

        if (!roundEnv.processingOver() || generated || routes.isEmpty()) {
            return false;
        }

        generated = true;
        generateSources();
        return false;
    }

    private void generateSources() {
        String registrarSimpleName = "GeneratedRouteRegistrar_" + Integer.toHexString(routes.keySet().toString().hashCode());
        String registrarQualifiedName = GENERATED_PACKAGE + "." + registrarSimpleName;
        generateRegistrarSource(registrarSimpleName);
        generateServiceDescriptor(registrarQualifiedName);
        generateApplicationModel();
    }

    private void generateRegistrarSource(String registrarSimpleName) {
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject file = filer.createSourceFile(GENERATED_PACKAGE + "." + registrarSimpleName);
            try (Writer writer = file.openWriter()) {
                writer.write("package " + GENERATED_PACKAGE + ";\n\n");
                writer.write("public final class " + registrarSimpleName
                        + " implements com.tanwir.resteasy.reactive.server.GeneratedRouteRegistrar {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(com.tanwir.resteasy.reactive.server.RouteRegistrar registrar) {\n");
                for (RouteDefinition route : routes.values()) {
                    writer.write("        registrar.registerGet(\"" + escape(route.path) + "\", ");
                    writer.write("\"" + escape(route.operationId) + "\", ");
                    writer.write(route.resourceClass + ".class, ");
                    writer.write("resource -> resource." + route.methodName + "());\n");
                }
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate GET route registrar", e);
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
            throw new IllegalStateException("Unable to generate GET route service descriptor", e);
        }
    }

    private void generateApplicationModel() {
        Filer filer = processingEnv.getFiler();
        Properties properties = new Properties();
        properties.setProperty("application.name", resolveApplicationName());
        properties.setProperty("route.count", Integer.toString(routes.size()));
        int index = 0;
        for (RouteDefinition route : routes.values()) {
            properties.setProperty("route." + index + ".path", route.path);
            properties.setProperty("route." + index + ".operation-id", route.operationId);
            index++;
        }

        try {
            FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                    MiniApplicationModelConstants.RESOURCE_PATH);
            try (Writer writer = file.openWriter(); StringWriter buffer = new StringWriter()) {
                properties.store(buffer, "Generated by MiniResteasyReactiveProcessor");
                writer.write(buffer.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate application model", e);
        }
    }

    private String resolveApplicationName() {
        return processingEnv.getOptions().getOrDefault(
                MiniApplicationModelConstants.OPTION_APPLICATION_NAME,
                "mini-quarkus-application");
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class RouteDefinition {

        private final String path;
        private final String operationId;
        private final String resourceClass;
        private final String methodName;

        private RouteDefinition(String path, String operationId, String resourceClass, String methodName) {
            this.path = path;
            this.operationId = operationId;
            this.resourceClass = resourceClass;
            this.methodName = methodName;
        }

        private static RouteDefinition create(ExecutableElement method, ProcessingEnvironment processingEnv) {
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@GET methods must be public in the first REST prototype", method);
                return null;
            }
            if (!method.getParameters().isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@GET methods must not declare parameters in the first REST prototype", method);
                return null;
            }
            if (method.getReturnType().getKind() != TypeKind.DECLARED
                    || !"java.lang.String".equals(processingEnv.getTypeUtils().erasure(method.getReturnType()).toString())) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@GET methods must return String in the first REST prototype", method);
                return null;
            }

            TypeElement resourceClass = (TypeElement) method.getEnclosingElement();
            if (!resourceClass.getModifiers().contains(Modifier.PUBLIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Resource classes must be public", resourceClass);
                return null;
            }
            if (resourceClass.getAnnotation(Singleton.class) == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Resource classes must also be annotated with @Singleton so ArC can manage them", resourceClass);
                return null;
            }

            Path classPath = resourceClass.getAnnotation(Path.class);
            if (classPath == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Resource classes must be annotated with @Path", resourceClass);
                return null;
            }

            Path methodPath = method.getAnnotation(Path.class);
            String path = normalizePath(classPath.value(), methodPath == null ? "" : methodPath.value());
            String resourceClassName = resourceClass.getQualifiedName().toString();
            String operationId = resourceClassName + "#" + method.getSimpleName();
            return new RouteDefinition(path, operationId, resourceClassName, method.getSimpleName().toString());
        }

        private static String normalizePath(String classPath, String methodPath) {
            String classSegment = normalizeSegment(classPath);
            String methodSegment = normalizeSegment(methodPath);
            if (classSegment.isEmpty() && methodSegment.isEmpty()) {
                return "/";
            }
            if (classSegment.isEmpty()) {
                return "/" + methodSegment;
            }
            if (methodSegment.isEmpty()) {
                return "/" + classSegment;
            }
            return "/" + classSegment + "/" + methodSegment;
        }

        private static String normalizeSegment(String value) {
            if (value == null) {
                return "";
            }
            String normalized = value.trim();
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }
}
