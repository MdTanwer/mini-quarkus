package com.tanwir.config.processor;

import com.tanwir.config.ConfigProperty;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Annotation processor for @ConfigProperty.
 * Generates configuration injection code at compile time.
 */
@SupportedAnnotationTypes("com.tanwir.config.ConfigProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ConfigProcessor extends AbstractProcessor {
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        
        // Collect all classes that use @ConfigProperty
        Map<String, List<ConfigInjectionPoint>> configClasses = new HashMap<>();
        
        for (Element element : roundEnv.getElementsAnnotatedWith(ConfigProperty.class)) {
            if (element.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) element;
                TypeElement enclosingClass = (TypeElement) field.getEnclosingElement();
                
                ConfigInjectionPoint injectionPoint = new ConfigInjectionPoint(
                    field.getSimpleName().toString(),
                    field.asType().toString(),
                    field.getAnnotation(ConfigProperty.class)
                );
                
                configClasses.computeIfAbsent(
                    enclosingClass.getQualifiedName().toString(),
                    k -> new ArrayList<>()
                ).add(injectionPoint);
            }
        }
        
        // Generate configuration loader for each class
        for (Map.Entry<String, List<ConfigInjectionPoint>> entry : configClasses.entrySet()) {
            generateConfigLoader(entry.getKey(), entry.getValue());
        }
        
        return false;
    }
    
    private void generateConfigLoader(String className, List<ConfigInjectionPoint> injectionPoints) {
        try {
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            String loaderClassName = simpleClassName + "ConfigLoader";
            String packageName = className.substring(0, className.lastIndexOf('.'));
            
            JavaFileObject loaderFile = processingEnv.getFiler()
                .createSourceFile(packageName + "." + loaderClassName);
            
            try (PrintWriter out = new PrintWriter(loaderFile.openWriter())) {
                generateConfigLoaderClass(out, packageName, loaderClassName, simpleClassName, injectionPoints);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate config loader for " + className + ": " + e.getMessage()
            );
        }
    }
    
    private void generateConfigLoaderClass(PrintWriter out, String packageName, 
                                         String loaderClassName, String targetClassName,
                                         List<ConfigInjectionPoint> injectionPoints) {
        
        out.println("package " + packageName + ";");
        out.println();
        out.println("import com.tanwir.config.MiniConfig;");
        out.println();
        out.println("/**");
        out.println(" * Generated configuration loader for " + targetClassName);
        out.println(" */");
        out.println("public class " + loaderClassName + " {");
        out.println();
        out.println("    private final MiniConfig config = MiniConfig.getInstance();");
        out.println();
        
        // Generate method to inject configuration into target instance
        out.println("    public void inject(" + targetClassName + " target) {");
        for (ConfigInjectionPoint injectionPoint : injectionPoints) {
            String fieldName = injectionPoint.fieldName;
            String configKey = getConfigKey(injectionPoint);
            String defaultValue = injectionPoint.annotation.defaultValue();
            
            out.println("        try {");
            out.println("            target." + fieldName + " = " + 
                        getConversionExpression(injectionPoint, configKey, defaultValue) + ";");
            out.println("        } catch (Exception e) {");
            out.println("            throw new RuntimeException(\"Failed to inject configuration for '" + 
                        fieldName + "'\", e);");
            out.println("        }");
            out.println();
        }
        out.println("    }");
        out.println();
        
        // Generate static convenience method
        out.println("    public static " + targetClassName + " createWithConfig(" + targetClassName + " target) {");
        out.println("        " + loaderClassName + " loader = new " + loaderClassName + "();");
        out.println("        loader.inject(target);");
        out.println("        return target;");
        out.println("    }");
        out.println("}");
    }
    
    private String getConfigKey(ConfigInjectionPoint injectionPoint) {
        String name = injectionPoint.annotation.name();
        return name.isEmpty() ? injectionPoint.fieldName : name;
    }
    
    private String getConversionExpression(ConfigInjectionPoint injectionPoint, 
                                          String configKey, String defaultValue) {
        String fieldType = injectionPoint.fieldType;
        
        // Handle primitive types and their boxed equivalents
        if (fieldType.equals("String") || fieldType.equals("java.lang.String")) {
            if (defaultValue.isEmpty()) {
                return "config.getValue(\"" + configKey + "\")";
            } else {
                return "config.getValue(\"" + configKey + "\", \"" + defaultValue + "\")";
            }
        } else if (fieldType.equals("int") || fieldType.equals("java.lang.Integer")) {
            if (defaultValue.isEmpty()) {
                return "config.getIntegerValue(\"" + configKey + "\")";
            } else {
                return "config.getIntegerValue(\"" + configKey + "\", " + defaultValue + ")";
            }
        } else if (fieldType.equals("boolean") || fieldType.equals("java.lang.Boolean")) {
            if (defaultValue.isEmpty()) {
                return "config.getBooleanValue(\"" + configKey + "\")";
            } else {
                return "config.getBooleanValue(\"" + configKey + "\", " + Boolean.parseBoolean(defaultValue) + ")";
            }
        } else {
            // For other types, use String and let the user handle conversion
            if (defaultValue.isEmpty()) {
                return "config.getValue(\"" + configKey + "\")";
            } else {
                return "config.getValue(\"" + configKey + "\", \"" + defaultValue + "\")";
            }
        }
    }
    
    private static class ConfigInjectionPoint {
        final String fieldName;
        final String fieldType;
        final ConfigProperty annotation;
        
        ConfigInjectionPoint(String fieldName, String fieldType, ConfigProperty annotation) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.annotation = annotation;
        }
    }
}
