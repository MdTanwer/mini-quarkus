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
import com.tanwir.resteasy.reactive.server.POST;
import com.tanwir.resteasy.reactive.server.PUT;
import com.tanwir.resteasy.reactive.server.DELETE;
import com.tanwir.resteasy.reactive.server.PATCH;
import com.tanwir.resteasy.reactive.server.PathParam;
import com.tanwir.resteasy.reactive.server.QueryParam;
import com.tanwir.resteasy.reactive.server.Produces;
import com.tanwir.resteasy.reactive.server.Consumes;

@SupportedAnnotationTypes({
    "jakarta.ws.rs.GET",
    "com.tanwir.resteasy.reactive.server.POST",
    "com.tanwir.resteasy.reactive.server.PUT",
    "com.tanwir.resteasy.reactive.server.DELETE",
    "com.tanwir.resteasy.reactive.server.PATCH"
})
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
        // Process GET methods
        for (Element element : roundEnv.getElementsAnnotatedWith(GET.class)) {
            processRoute(element, "GET");
        }
        // Process POST methods
        for (Element element : roundEnv.getElementsAnnotatedWith(POST.class)) {
            processRoute(element, "POST");
        }
        // Process PUT methods
        for (Element element : roundEnv.getElementsAnnotatedWith(PUT.class)) {
            processRoute(element, "PUT");
        }
        // Process DELETE methods
        for (Element element : roundEnv.getElementsAnnotatedWith(DELETE.class)) {
            processRoute(element, "DELETE");
        }
        // Process PATCH methods
        for (Element element : roundEnv.getElementsAnnotatedWith(PATCH.class)) {
            processRoute(element, "PATCH");
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
                    switch (route.httpMethod) {
                        case "GET":
                            writer.write("        registrar.registerGet(\"" + escape(route.path) + "\", ");
                            break;
                        case "POST":
                            writer.write("        registrar.registerPost(\"" + escape(route.path) + "\", ");
                            break;
                        case "PUT":
                            writer.write("        registrar.registerPut(\"" + escape(route.path) + "\", ");
                            break;
                        case "DELETE":
                            writer.write("        registrar.registerDelete(\"" + escape(route.path) + "\", ");
                            break;
                        case "PATCH":
                            writer.write("        registrar.registerPatch(\"" + escape(route.path) + "\", ");
                            break;
                        default:
                            throw new IllegalStateException("Unsupported HTTP method: " + route.httpMethod);
                    }
                    writer.write("\"" + escape(route.operationId) + "\", ");
                    writer.write(route.resourceClass + ".class, ");
                    writer.write("(resource, request, pathParams, queryParams, body) -> {\n");
                    writer.write("    // Simple parameter extraction for Phase 3\n");
                    writer.write("    try {\n");
                    writer.write("        return (Object) resource." + route.methodName + "();\n");
                    writer.write("    } catch (Exception e) {\n");
                    writer.write("        throw new RuntimeException(\"Method invocation failed\", e);\n");
                    writer.write("    }\n");
                    writer.write("});\n");
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

    private void processRoute(Element element, String httpMethod) {
        if (element.getKind() != ElementKind.METHOD) {
            error(element, "@" + httpMethod + " can only be used on methods");
            return;
        }
        ExecutableElement method = (ExecutableElement) element;
        RouteDefinition route = RouteDefinition.create(method, httpMethod, processingEnv);
        if (route != null) {
            routes.put(route.operationId, route);
        }
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class RouteDefinition {

        private final String httpMethod;
        private final String path;
        private final String operationId;
        private final String resourceClass;
        private final String methodName;
        private final boolean hasParameters;

        private RouteDefinition(String httpMethod, String path, String operationId, String resourceClass, String methodName, boolean hasParameters) {
            this.httpMethod = httpMethod;
            this.path = path;
            this.operationId = operationId;
            this.resourceClass = resourceClass;
            this.methodName = methodName;
            this.hasParameters = hasParameters;
        }

        private static RouteDefinition create(ExecutableElement method, String httpMethod, ProcessingEnvironment processingEnv) {
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@" + httpMethod + " methods must be public", method);
                return null;
            }
            
            // Validate return type - can be String, Response, or any POJO (for JSON serialization)
            TypeKind returnTypeKind = method.getReturnType().getKind();
            if (returnTypeKind != TypeKind.DECLARED && returnTypeKind != TypeKind.VOID) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@" + httpMethod + " methods must return a declared type or void", method);
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
            boolean hasParameters = !method.getParameters().isEmpty();
            return new RouteDefinition(httpMethod, path, operationId, resourceClassName, method.getSimpleName().toString(), hasParameters);
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
