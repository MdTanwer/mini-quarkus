package com.tanwir.miniquarkus.processor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import com.tanwir.miniquarkus.processor.DotNames;

/**
 * Reflections utility class following Quarkus ARC patterns.
 * Provides reflection utilities for bytecode generation and runtime access.
 */
public class Reflections {

    /**
     * Creates a new instance of the specified class using reflection.
     */
    public static Object newInstance(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            if (!constructor.canAccess()) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + clazz.getName(), e);
        }
    }

    /**
     * Finds a constructor with the specified parameter types.
     */
    public static Constructor<?> findConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            return clazz.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Constructor not found: " + clazz.getName() + 
                "(" + Arrays.toString(parameterTypes) + ")", e);
        }
    }

    /**
     * Finds a method with the specified name and parameter types.
     */
    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method not found: " + clazz.getName() + "." + name + 
                "(" + Arrays.toString(parameterTypes) + ")", e);
        }
    }

    /**
     * Finds a method with the specified name and parameter types, including inherited methods.
     */
    public static Method findMethodInHierarchy(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method not found in hierarchy: " + clazz.getName() + "." + name + 
                "(" + Arrays.toString(parameterTypes) + ")", e);
        }
    }

    /**
     * Finds a field with the specified name.
     */
    public static Field findField(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            if (!field.canAccess()) {
                field.setAccessible(true);
            }
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found: " + clazz.getName() + "." + name, e);
        }
    }

    /**
     * Invokes a method using reflection.
     */
    public static Object invokeMethod(Method method, Object target, Object... args) {
        try {
            if (!method.canAccess()) {
                method.setAccessible(true);
            }
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + method.getName(), e);
        }
    }

    /**
     * Gets the value of a field using reflection.
     */
    public static Object getFieldValue(Field field, Object target) {
        try {
            if (!field.canAccess()) {
                field.setAccessible(true);
            }
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field value: " + field.getName(), e);
        }
    }

    /**
     * Sets the value of a field using reflection.
     */
    public static void setFieldValue(Field field, Object target, Object value) {
        try {
            if (!field.canAccess()) {
                field.setAccessible(true);
            }
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field value: " + field.getName(), e);
        }
    }

    /**
     * Checks if a method needs reflection fallback based on visibility and package.
     */
    public static boolean needsReflectionFallback(MethodInfo method, String targetPackage) {
        if (java.lang.reflect.Modifier.isPublic(method.flags())) {
            return false;
        }
        // Reflection fallback is needed for private methods
        if (java.lang.reflect.Modifier.isPrivate(method.flags())) {
            return true;
        }
        // Non-public methods declared on superclasses in different packages need reflection
        return !DotNames.packagePrefix(method.declaringClass().name()).equals(targetPackage);
    }

    /**
     * Checks if a field needs reflection fallback based on visibility and package.
     */
    public static boolean needsReflectionFallback(org.jboss.jandex.FieldInfo field, String targetPackage, ClassInfo beanClass) {
        if (java.lang.reflect.Modifier.isPublic(field.flags())) {
            return false;
        }
        // Reflection fallback is needed for private fields if transformation is off OR field's declaring class != bean class
        if (java.lang.reflect.Modifier.isPrivate(field.flags())) {
            return true;
        }
        // Non-public fields declared on superclasses in different packages need reflection
        return !DotNames.packagePrefix(field.declaringClass().name()).equals(targetPackage) ||
               !field.declaringClass().name().equals(beanClass.name());
    }

    /**
     * Gets the method descriptor for a given method info.
     */
    public static String getMethodDescriptor(MethodInfo method) {
        StringBuilder descriptor = new StringBuilder();
        descriptor.append("(");
        
        for (Type paramType : method.parameterTypes()) {
            descriptor.append(getTypeDescriptor(paramType));
        }
        
        descriptor.append(")").append(getTypeDescriptor(method.returnType()));
        return descriptor.toString();
    }

    /**
     * Gets the type descriptor for a given type.
     */
    private static String getTypeDescriptor(Type type) {
        if (type.kind() == org.jboss.jandex.Type.Kind.VOID) {
            return "V";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.BOOLEAN) {
            return "Z";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.BYTE) {
            return "B";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.CHAR) {
            return "C";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.SHORT) {
            return "S";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.INT) {
            return "I";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.LONG) {
            return "J";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.FLOAT) {
            return "F";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.DOUBLE) {
            return "D";
        } else if (type.kind() == org.jboss.jandex.Type.Kind.ARRAY) {
            return "[" + getTypeDescriptor(type.asArrayType().component());
        } else {
            return "L" + type.name().replace('.', '/') + ";";
        }
    }

    /**
     * Prevent instantiation.
     */
    private Reflections() {
        throw new UnsupportedOperationException("Utility class");
    }
}
