package com.tanwir.arc;

import com.tanwir.arc.context.ApplicationContext;
import com.tanwir.arc.context.DependentContext;
import com.tanwir.arc.context.RequestContext;
import com.tanwir.arc.context.RequestContextController;
import com.tanwir.arc.context.SingletonContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleArcContainer implements ArcContainer, BeanRegistrar {

    // One map: type → descriptor (replaces both factories + singletons maps)
    private final Map<Class<?>, BeanDescriptor<?>> descriptors = new ConcurrentHashMap<>();
    
    // One context per scope
    private final SingletonContext singletonContext = new SingletonContext();
    private final ApplicationContext applicationContext = new ApplicationContext();
    private final RequestContext requestContext = new RequestContext();
    private final DependentContext dependentContext = new DependentContext();
    
    // Qualifier-aware lookup: Map<Class<?>, List<BeanDescriptor<?>>>
    // for when multiple beans of same type exist, differentiated by qualifier
    private final Map<Class<?>, List<BeanDescriptor<?>>> byType = new ConcurrentHashMap<>();
    
    private final ThreadLocal<Deque<Class<?>>> creationStack = ThreadLocal.withInitial(ArrayDeque::new);
    private final RequestContextController requestContextController = new RequestContextController(requestContext);

    <T> void registerReflectiveSingleton(Class<T> beanType) {
        if (!beanType.isAnnotationPresent(Singleton.class)) {
            throw new IllegalArgumentException(beanType.getName() + " must be annotated with @Singleton");
        }
        BeanDescriptor<T> descriptor = new BeanDescriptor<>(
            beanType, 
            Scope.SINGLETON, 
            container -> create(beanType),
            null, // postConstructMethod
            null, // preDestroyMethod
            Set.of() // qualifiers
        );
        register(descriptor);
    }

    
    @Override
    public <T> void register(BeanDescriptor<T> descriptor) {
        BeanDescriptor<?> previous = descriptors.putIfAbsent(descriptor.beanClass(), descriptor);
        if (previous != null) {
            throw new IllegalStateException("A bean descriptor is already registered for " + descriptor.beanClass().getName());
        }
        
        // Update byType map for qualifier-aware lookup
        byType.computeIfAbsent(descriptor.beanClass(), k -> new java.util.ArrayList<>()).add(descriptor);
    }

    @Override
    public <T> InstanceHandle<T> instance(Class<T> type) {
        return instance(type, null);
    }

    public <T> InstanceHandle<T> instance(Class<T> type, Class<? extends Annotation> qualifier) {
        BeanDescriptor<T> descriptor = findDescriptor(type, qualifier);
        if (descriptor == null) {
            throw new IllegalStateException("No bean registered for " + type.getName() + 
                (qualifier != null ? " with qualifier " + qualifier.getName() : ""));
        }
        
        T instance = getOrCreate(descriptor);
        return new SimpleInstanceHandle<>(instance, descriptor);
    }

    public RequestContextController requestContextController() {
        return requestContextController;
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrCreate(BeanDescriptor<T> descriptor) {
        Deque<Class<?>> stack = creationStack.get();
        if (stack.contains(descriptor.beanClass())) {
            throw new IllegalStateException("Circular dependency detected: " + formatCircularDependency(stack, descriptor.beanClass()));
        }
        stack.push(descriptor.beanClass());
        try {
            return switch (descriptor.scope()) {
                case SINGLETON -> singletonContext.getOrCreate(descriptor, this);
                case APPLICATION -> applicationContext.getOrCreate(descriptor, this);
                case REQUEST -> requestContext.getOrCreate(descriptor, this);
                case DEPENDENT -> dependentContext.getOrCreate(descriptor, this);
            };
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                creationStack.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> BeanDescriptor<T> findDescriptor(Class<T> type, Class<? extends Annotation> qualifier) {
        // First try exact match
        BeanDescriptor<?> descriptor = descriptors.get(type);
        if (descriptor != null) {
            if (qualifier == null || descriptor.qualifiers().contains(qualifier)) {
                return (BeanDescriptor<T>) descriptor;
            }
        }
        
        // If qualifier specified, search byType list
        if (qualifier != null) {
            List<BeanDescriptor<?>> candidates = byType.get(type);
            if (candidates != null) {
                for (BeanDescriptor<?> candidate : candidates) {
                    if (candidate.qualifiers().contains(qualifier)) {
                        return (BeanDescriptor<T>) candidate;
                    }
                }
            }
        }
        
        return null;
    }

    public void destroyAll() {
        singletonContext.destroy();
        applicationContext.destroy();
        requestContext.destroy();
        dependentContext.destroy();
    }

    private static String formatCircularDependency(Deque<Class<?>> stack, Class<?> repeatedType) {
        StringBuilder message = new StringBuilder();
        for (Class<?> type : stack) {
            if (!message.isEmpty()) {
                message.append(" -> ");
            }
            message.append(type.getName());
        }
        if (!message.isEmpty()) {
            message.append(" -> ");
        }
        return message.append(repeatedType.getName()).toString();
    }

    private static <T> T create(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate singleton bean " + type.getName(), e);
        }
    }

    private static final class SimpleInstanceHandle<T> implements InstanceHandle<T> {

        private final T instance;
        private final BeanDescriptor<T> descriptor;

        private SimpleInstanceHandle(T instance, BeanDescriptor<T> descriptor) {
            this.instance = instance;
            this.descriptor = descriptor;
        }

        @Override
        public T get() {
            return instance;
        }

        @Override
        public void close() {
            if (descriptor.scope() == Scope.DEPENDENT) {
                // For @Dependent beans, @PreDestroy is triggered by InstanceHandle.close()
                invokePreDestroy(instance);
            }
        }

        private void invokePreDestroy(Object instance) {
            if (descriptor.preDestroyMethod() != null) {
                try {
                    java.lang.reflect.Method method = instance.getClass().getMethod(descriptor.preDestroyMethod());
                    method.invoke(instance);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke @PreDestroy method", e);
                }
            } else {
                // Search for @PreDestroy annotated methods
                for (java.lang.reflect.Method method : instance.getClass().getMethods()) {
                    if (method.isAnnotationPresent(PreDestroy.class)) {
                        try {
                            method.invoke(instance);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to invoke @PreDestroy method", e);
                        }
                    }
                }
            }
        }
    }
}
