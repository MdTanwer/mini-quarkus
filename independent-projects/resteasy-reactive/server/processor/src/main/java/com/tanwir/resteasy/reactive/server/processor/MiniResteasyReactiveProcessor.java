package com.tanwir.resteasy.reactive.server.processor;

import com.tanwir.arc.Singleton;
import com.tanwir.bootstrap.model.MiniApplicationModelConstants;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import javax.lang.model.element.VariableElement;
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
                    // Declare local variables for each bound parameter
                    for (int i = 0; i < route.params.size(); i++) {
                        RouteDefinition.ParamBinding p = route.params.get(i);
                        String varName = "param" + i;
                        switch (p.kind()) {
                            case PATH -> writer.write(
                                    "        " + p.javaType() + " " + varName
                                    + " = " + castFromString("pathParams.get(\"" + p.annotationName() + "\")", p.javaType()) + ";\n");
                            case QUERY -> writer.write(
                                    "        " + p.javaType() + " " + varName
                                    + " = " + castFromString("queryParams.get(\"" + p.annotationName() + "\")", p.javaType()) + ";\n");
                            case BODY -> {
                                if ("io.vertx.core.json.JsonObject".equals(p.javaType())) {
                                    writer.write("        " + p.javaType() + " " + varName + " = body;\n");
                                } else {
                                    writer.write("        " + p.javaType() + " " + varName
                                            + " = body != null ? body.mapTo(" + p.javaType() + ".class) : null;\n");
                                }
                            }
                        }
                    }
                    // Build argument list
                    StringBuilder args = new StringBuilder();
                    for (int i = 0; i < route.params.size(); i++) {
                        if (i > 0) args.append(", ");
                        args.append("param").append(i);
                    }
                    if (route.transactional) {
                        // Wrap the invocation in a JDBC transaction.
                        // Mirrors Quarkus's TransactionInterceptor woven at build time via CDI.
                        writer.write("    com.tanwir.panache.TransactionManager.begin();\n");
                        writer.write("    try {\n");
                        writer.write("        Object _result = (Object) resource." + route.methodName + "(" + args + ");\n");
                        writer.write("        com.tanwir.panache.TransactionManager.commit();\n");
                        writer.write("        return _result;\n");
                        writer.write("    } catch (Exception e) {\n");
                        writer.write("        com.tanwir.panache.TransactionManager.rollback();\n");
                        writer.write("        throw new RuntimeException(\"Transactional method failed\", e);\n");
                        writer.write("    }\n");
                    } else {
                        writer.write("    try {\n");
                        writer.write("        return (Object) resource." + route.methodName + "(" + args + ");\n");
                        writer.write("    } catch (Exception e) {\n");
                        writer.write("        throw new RuntimeException(\"Method invocation failed\", e);\n");
                        writer.write("    }\n");
                    }
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

    /**
     * Generates a Java expression that converts a String value to the target type.
     * Supports String, int/Integer, long/Long, boolean/Boolean — same conversions
     * that Quarkus's ParameterExtractor handles via codec chains.
     */
    private static String castFromString(String expr, String javaType) {
        return switch (javaType) {
            case "java.lang.String", "String" -> expr;
            case "int", "java.lang.Integer" -> "Integer.parseInt(" + expr + ")";
            case "long", "java.lang.Long" -> "Long.parseLong(" + expr + ")";
            case "boolean", "java.lang.Boolean" -> "Boolean.parseBoolean(" + expr + ")";
            case "double", "java.lang.Double" -> "Double.parseDouble(" + expr + ")";
            case "float", "java.lang.Float" -> "Float.parseFloat(" + expr + ")";
            case "java.math.BigDecimal" -> "new java.math.BigDecimal(" + expr + ")";
            default -> "((" + javaType + ") " + expr + ")";
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class RouteDefinition {

        private static final String TRANSACTIONAL_FQN = "com.tanwir.panache.Transactional";

        enum ParamKind { PATH, QUERY, BODY }

        record ParamBinding(ParamKind kind, String annotationName, String javaType) {}

        private final String httpMethod;
        private final String path;
        private final String operationId;
        private final String resourceClass;
        private final String methodName;
        private final List<ParamBinding> params;
        /**
         * True when the method or its declaring class carries {@code @Transactional}.
         * When true the generated route handler wraps the call in a JDBC transaction —
         * same pattern as Quarkus's {@code TransactionInterceptor} in Narayana JTA.
         */
        private final boolean transactional;

        private RouteDefinition(String httpMethod, String path, String operationId,
                String resourceClass, String methodName, List<ParamBinding> params,
                boolean transactional) {
            this.httpMethod = httpMethod;
            this.path = path;
            this.operationId = operationId;
            this.resourceClass = resourceClass;
            this.methodName = methodName;
            this.params = params;
            this.transactional = transactional;
        }

        private static RouteDefinition create(ExecutableElement method, String httpMethod, ProcessingEnvironment processingEnv) {
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@" + httpMethod + " methods must be public", method);
                return null;
            }

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

            // Build per-parameter binding descriptors.
            // Each parameter is classified as PATH, QUERY, or BODY based on its annotations.
            // Mirrors what Quarkus's ResteasyReactiveProcessor does when building ParameterExtractor chains.
            List<ParamBinding> params = new ArrayList<>();
            for (VariableElement param : method.getParameters()) {
                String paramType = processingEnv.getTypeUtils().erasure(param.asType()).toString();
                String pathName = annotationValue(param, "com.tanwir.resteasy.reactive.server.PathParam",
                        "jakarta.ws.rs.PathParam");
                String queryName = annotationValue(param, "com.tanwir.resteasy.reactive.server.QueryParam",
                        "jakarta.ws.rs.QueryParam");

                if (pathName != null) {
                    params.add(new ParamBinding(ParamKind.PATH, pathName, paramType));
                } else if (queryName != null) {
                    params.add(new ParamBinding(ParamKind.QUERY, queryName, paramType));
                } else {
                    // Unannotated parameter → body. The type decides how to deserialize.
                    params.add(new ParamBinding(ParamKind.BODY, null, paramType));
                }
            }

            // Detect @Transactional by FQN on the method or the enclosing class.
            // Mirrors Quarkus's interceptor binding resolution in CDI, where @Transactional
            // on a class applies to all its methods.
            boolean isTransactional = hasAnnotationByFqn(method, TRANSACTIONAL_FQN)
                    || hasAnnotationByFqn(resourceClass, TRANSACTIONAL_FQN);

            return new RouteDefinition(httpMethod, path, operationId, resourceClassName,
                    method.getSimpleName().toString(), params, isTransactional);
        }

        /** Returns true if {@code element} is annotated with the given FQN annotation. */
        private static boolean hasAnnotationByFqn(javax.lang.model.element.Element element, String fqn) {
            for (javax.lang.model.element.AnnotationMirror am : element.getAnnotationMirrors()) {
                if (am.getAnnotationType().toString().equals(fqn)) {
                    return true;
                }
            }
            return false;
        }

        /** Returns the first non-empty {@code value} attribute from the matching annotation, or null. */
        private static String annotationValue(VariableElement param, String... fqns) {
            Set<String> fqnSet = new java.util.HashSet<>(java.util.Arrays.asList(fqns));
            for (javax.lang.model.element.AnnotationMirror am : param.getAnnotationMirrors()) {
                if (!fqnSet.contains(am.getAnnotationType().toString())) {
                    continue;
                }
                for (Map.Entry<? extends javax.lang.model.element.ExecutableElement,
                        ? extends javax.lang.model.element.AnnotationValue> entry :
                        am.getElementValues().entrySet()) {
                    if ("value".equals(entry.getKey().getSimpleName().toString())) {
                        return entry.getValue().getValue().toString();
                    }
                }
                // annotation present but value() not set — use the parameter name
                return param.getSimpleName().toString();
            }
            return null;
        }

        private static String normalizePath(String classPath, String methodPath) {
            String classSegment = normalizeSegment(classPath);
            String methodSegment = normalizeSegment(methodPath);
            if (classSegment.isEmpty() && methodSegment.isEmpty()) return "/";
            if (classSegment.isEmpty()) return "/" + methodSegment;
            if (methodSegment.isEmpty()) return "/" + classSegment;
            return "/" + classSegment + "/" + methodSegment;
        }

        private static String normalizeSegment(String value) {
            if (value == null) return "";
            String normalized = value.trim();
            while (normalized.startsWith("/")) normalized = normalized.substring(1);
            while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            return normalized;
        }
    }
}
