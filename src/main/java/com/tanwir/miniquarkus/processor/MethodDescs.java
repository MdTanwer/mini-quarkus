package com.tanwir.miniquarkus.processor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.interceptor.InvocationContext;

import com.tanwir.miniquarkus.processor.DotNames;

import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * MethodDescs utility class following Quarkus ARC patterns.
 * Contains commonly used MethodDesc constants for bytecode generation.
 */
public final class MethodDescs {

    // Core reflection methods
    public static final MethodDesc OBJECT_CONSTRUCTOR = ConstructorDesc.of(Object.class);
    public static final MethodDesc OBJECT_HASH_CODE = MethodDesc.of(Object.class, "hashCode", int.class);
    public static final MethodDesc OBJECT_EQUALS = MethodDesc.of(Object.class, "equals", boolean.class, Object.class);
    public static final MethodDesc OBJECT_TO_STRING = MethodDesc.of(Object.class, "toString", String.class);

    // Class methods
    public static final MethodDesc CLASS_GET_NAME = MethodDesc.of(Class.class, "getName", String.class);
    public static final MethodDesc CLASS_GET_SIMPLE_NAME = MethodDesc.of(Class.class, "getSimpleName", String.class);
    public static final MethodDesc CLASS_GET_DECLARED_CONSTRUCTOR = MethodDesc.of(Class.class, "getDeclaredConstructor", Constructor.class, Class[].class);
    public static final MethodDesc CLASS_GET_DECLARED_METHOD = MethodDesc.of(Class.class, "getDeclaredMethod", Method.class, String.class, Class[].class);
    public static final MethodDesc CLASS_GET_DECLARED_FIELD = MethodDesc.of(Class.class, "getDeclaredField", Field.class, String.class);

    // Thread methods
    public static final MethodDesc THREAD_GET_TCCL = MethodDesc.of(Thread.class, "getContextClassLoader", ClassLoader.class);
    public static final MethodDesc THREAD_CURRENT_THREAD = MethodDesc.of(Thread.class, "currentThread", Thread.class);

    // String methods
    public static final MethodDesc STRING_FORMAT = MethodDesc.of(String.class, "format", String.class, Object.class, Object[].class);

    // Collection methods
    public static final MethodDesc LIST_ADD = MethodDesc.of(List.class, "add", boolean.class, Object.class);
    public static final MethodDesc LIST_GET = MethodDesc.of(List.class, "get", Object.class, int.class);
    public static final MethodDesc LIST_SIZE = MethodDesc.of(List.class, "size", int.class);
    public static final MethodDesc MAP_PUT = MethodDesc.of(Map.class, "put", Object.class, Object.class, Object.class);
    public static final MethodDesc MAP_GET = MethodDesc.of(Map.class, "get", Object.class, Object.class);
    public static final MethodDesc SET_ADD = MethodDesc.of(Set.class, "add", boolean.class, Object.class);
    public static final MethodDesc SET_OF = MethodDesc.of(Set.class, "of", Set.class, Object[].class);

    // Function interfaces
    public static final MethodDesc SUPPLIER_GET = MethodDesc.of(Supplier.class, "get", Object.class);
    public static final MethodDesc CONSUMER_ACCEPT = MethodDesc.of(Consumer.class, "accept", void.class, Object.class);
    public static final MethodDesc FUNCTION_APPLY = MethodDesc.of(Function.class, "apply", Object.class, Object.class);
    public static final MethodDesc BIFUNCTION_APPLY = MethodDesc.of(BiFunction.class, "apply", Object.class, Object.class, Object.class);
    public static final MethodDesc PREDICATE_TEST = MethodDesc.of(Predicate.class, "test", boolean.class, Object.class);

    // Array methods
    public static final MethodDesc ARRAY_NEW_INSTANCE = MethodDesc.of(java.lang.reflect.Array.class, "newInstance", Object.class, Class.class, int.class);

    // Collections utility methods
    public static final MethodDesc COLLECTIONS_EMPTY_LIST = MethodDesc.of(Collections.class, "emptyList", List.class);
    public static final MethodDesc COLLECTIONS_EMPTY_SET = MethodDesc.of(Collections.class, "emptySet", Set.class);
    public static final MethodDesc COLLECTIONS_SINGLETON_LIST = MethodDesc.of(Collections.class, "singletonList", List.class, Object.class);
    public static final MethodDesc COLLECTIONS_SINGLETON_SET = MethodDesc.of(Collections.class, "singleton", Set.class, Object.class);

    // CDI-specific method descriptors
    public static final MethodDesc CONTEXTUAL_CREATE = MethodDesc.of(Contextual.class, "create", Object.class, CreationalContext.class);
    public static final MethodDesc INJECTABLE_BEAN_GET_TYPES = MethodDesc.of("jakarta.enterprise.inject.spi.Bean", "getTypes", Set.class);
    public static final MethodDesc INJECTABLE_BEAN_GET_QUALIFIERS = MethodDesc.of("jakarta.enterprise.inject.spi.Bean", "getQualifiers", Set.class);
    public static final MethodDesc INJECTABLE_BEAN_GET_SCOPE = MethodDesc.of("jakarta.enterprise.inject.spi.Bean", "getScope", Class.class);
    public static final MethodDesc INJECTABLE_BEAN_GET_BEAN_CLASS = MethodDesc.of("jakarta.enterprise.inject.spi.Bean", "getBeanClass", Class.class);
    public static final MethodDesc INJECTABLE_BEAN_GET_NAME = MethodDesc.of("jakarta.enterprise.inject.spi.Bean", "getName", String.class);

    // Quarkus ARC-specific method descriptors
    public static final MethodDesc ARC_CONTAINER = MethodDesc.of("io.quarkus.arc.Arc", "container", "io.quarkus.arc.ArcContainer");
    public static final MethodDesc ARC_CONTAINER_BEAN = MethodDesc.of("io.quarkus.arc.ArcContainer", "bean", "io.quarkus.arc.InjectableBean", String.class);
    public static final MethodDesc ARC_CONTAINER_GET_CONTEXTS = MethodDesc.of("io.quarkus.arc.ArcContainer", "getActiveContexts", List.class, Class.class);
    public static final MethodDesc ARC_CONTAINER_GET_ACTIVE_CONTEXT = MethodDesc.of("io.quarkus.arc.ArcContainer", "getActiveContext", "io.quarkus.arc.InjectableContext", Class.class);
    public static final MethodDesc CREATIONAL_CONTEXT_CHILD = MethodDesc.of("jakarta.enterprise.context.spi.CreationalContext", "child", CreationalContext.class);
    public static final MethodDesc INJECTABLE_CONTEXT_GET = MethodDesc.of("io.quarkus.arc.InjectableContext", "get", Object.class, "io.quarkus.arc.InjectableBean");
    public static final MethodDesc INJECTABLE_REF_PROVIDER_GET = MethodDesc.of("io.quarkus.arc.InjectableReferenceProvider", "get", Object.class, "io.quarkus.arc.InjectableBean");
    public static final MethodDesc REFLECTIONS_NEW_INSTANCE = MethodDesc.of("io.quarkus.arc.impl.Reflections", "newInstance", Object.class, Class.class);
    public static final MethodDesc REFLECTIONS_FIND_CONSTRUCTOR = MethodDesc.of("io.quarkus.arc.impl.Reflections", "findConstructor", Constructor.class, Class.class, Class[].class);
    public static final MethodDesc REFLECTIONS_FIND_METHOD = MethodDesc.of("io.quarkus.arc.impl.Reflections", "findMethod", Method.class, Class.class, String.class, Class[].class);
    public static final MethodDesc REFLECTIONS_INVOKE_METHOD = MethodDesc.of("io.quarkus.arc.impl.Reflections", "invokeMethod", Object.class, Class.class, String.class, Class[].class, Object[].class);

    // Client proxy method descriptors
    public static final MethodDesc CLIENT_PROXY_DELEGATE = MethodDesc.of("io.quarkus.arc.ClientProxy", "arc$delegate", Object.class);
    public static final MethodDesc CLIENT_PROXY_SET_MOCK = MethodDesc.of("io.quarkus.arc.ClientProxy", "arc$setMock", void.class, Object.class);
    public static final MethodDesc CLIENT_PROXY_CLEAR_MOCK = MethodDesc.of("io.quarkus.arc.ClientProxy", "arc$clearMock", void.class);

    // Subclass method descriptors
    public static final MethodDesc SUBCLASS_MARK_CONSTRUCTED = MethodDesc.of("io.quarkus.arc.Subclass", "arc$markConstructed", void.class);
    public static final MethodDesc SUBCLASS_DESTROY = MethodDesc.of("io.quarkus.arc.Subclass", "arc$destroy", void.class, Runnable.class);

    // Interception method descriptors
    public static final MethodDesc INVOCATION_CONTEXT_PROCEED = MethodDesc.of(InvocationContext.class, "proceed", Object.class);
    public static final MethodDesc INVOCATION_CONTEXT_GET_PARAMETERS = MethodDesc.of(InvocationContext.class, "getParameters", Object[].class);
    public static final MethodDesc INVOCATION_CONTEXT_GET_TARGET = MethodDesc.of(InvocationContext.class, "getTarget", Object.class);
    public static final MethodDesc INVOCATION_CONTEXT_GET_METHOD = MethodDesc.of(InvocationContext.class, "getMethod", Method.class);
    public static final MethodDesc INVOCATION_CONTEXT_GET_TIMER = MethodDesc.of(InvocationContext.class, "getTimer", Object.class);

    // Fixed value supplier
    public static final ConstructorDesc FIXED_VALUE_SUPPLIER_CONSTRUCTOR = ConstructorDesc.of("io.quarkus.arc.impl.FixedValueSupplier", Object.class, Object.class);

    // Prevent instantiation
    private MethodDescs() {
        throw new UnsupportedOperationException("Utility class");
    }
}
