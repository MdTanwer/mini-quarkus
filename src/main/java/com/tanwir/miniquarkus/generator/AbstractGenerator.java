package com.tanwir.miniquarkus.generator;

import java.lang.reflect.Modifier;
import java.util.function.Function;

import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;

import com.tanwir.miniquarkus.generator.ReflectionRegistration;
import com.tanwir.miniquarkus.generator.ResourceClassOutput;
import com.tanwir.miniquarkus.generator.ResourceOutput.Resource;
import com.tanwir.miniquarkus.generator.ResourceOutput.Resource.SpecialType;

import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Gizmo;

/**
 * Abstract base class for bytecode generators following Quarkus ARC patterns.
 * This mirrors the structure and functionality of Quarkus's AbstractGenerator.
 */
public abstract class AbstractGenerator {

    static final String DEFAULT_PACKAGE = "com.tanwir.miniquarkus.generator";
    static final String UNDERSCORE = "_";
    static final String SYNTHETIC_SUFFIX = "Synthetic";

    protected final boolean generateSources;
    protected final ReflectionRegistration reflectionRegistration;

    public AbstractGenerator(boolean generateSources, ReflectionRegistration reflectionRegistration) {
        this.generateSources = generateSources;
        this.reflectionRegistration = reflectionRegistration;
    }

    public AbstractGenerator(boolean generateSources) {
        this(generateSources, ReflectionRegistration.NOOP);
    }

    /**
     * Creates a Gizmo instance with proper configuration following Quarkus patterns.
     */
    protected static Gizmo gizmo(ClassOutput classOutput) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl == null) {
            throw new IllegalStateException("No TCCL available");
        }
        return Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false)
                .withLambdasAsAnonymousClasses(true);
    }

    /**
     * Generates a class name from a target package, base name and suffix.
     * This follows the exact pattern used in Quarkus ARC.
     */
    protected static String generatedNameFromTarget(String targetPackage, String baseName, String suffix) {
        if (targetPackage == null || targetPackage.isEmpty()) {
            return baseName + suffix;
        } else {
            return targetPackage + "." + baseName + suffix;
        }
    }

    /**
     * {@return a simple name of given {@code beanClassName}, stripped of {@code "_Bean"} suffix}
     */
    protected final String getBeanBaseName(String beanClassName) {
        String simpleName = beanClassName.contains(".")
                ? beanClassName.substring(beanClassName.lastIndexOf(".") + 1)
                : beanClassName;
        return simpleName.substring(0, simpleName.lastIndexOf("_Bean"));
    }

    /**
     * Determines if reflection fallback is needed for a method.
     * This follows Quarkus's visibility and package access rules.
     */
    protected final boolean isReflectionFallbackNeeded(MethodInfo method, String targetPackage) {
        if (Modifier.isPublic(method.flags())) {
            return false;
        }
        // Reflection fallback is needed for:
        // 1. private methods
        if (Modifier.isPrivate(method.flags())) {
            return true;
        }
        // 2. non-public methods declared on superclasses located in a different package
        return !packagePrefix(method.declaringClass().name()).equals(targetPackage);
    }

    /**
     * Determines if reflection fallback is needed for a field.
     * This follows Quarkus's field access and transformation rules.
     */
    protected final boolean isReflectionFallbackNeeded(FieldInfo field, String targetPackage, BeanInfo bean) {
        if (Modifier.isPublic(field.flags())) {
            return false;
        }
        // Reflection fallback is needed for:
        // 1. private fields if the transformation is turned off OR if the field's declaring class != bean class
        if (Modifier.isPrivate(field.flags())) {
            if (!bean.getDeployment().transformPrivateInjectedFields
                    || !field.declaringClass().name().equals(bean.getBeanClass())) {
                return true;
            }
        }
        // 2. for non-public fields declared on superclasses located in a different package
        return !packagePrefix(field.declaringClass().name()).equals(targetPackage);
    }

    /**
     * Extracts package prefix from a DotName.
     */
    private static String packagePrefix(DotName name) {
        String className = name.toString();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * Creates a ResourceClassOutput following Quarkus patterns.
     */
    protected ResourceClassOutput createResourceClassOutput(boolean isApplicationClass, 
            Function<String, SpecialType> specialTypeFunction) {
        return new ResourceClassOutput(isApplicationClass, specialTypeFunction, generateSources);
    }

    /**
     * Creates a ResourceClassOutput with no special types.
     */
    protected ResourceClassOutput createResourceClassOutput(boolean isApplicationClass) {
        return createResourceClassOutput(isApplicationClass, cn -> null);
    }
}
