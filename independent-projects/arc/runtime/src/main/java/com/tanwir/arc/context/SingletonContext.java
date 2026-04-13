package com.tanwir.arc.context;

import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.BeanDescriptor;
import com.tanwir.arc.Scope;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SingletonContext implements ScopeContext {
    private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();

    @Override
    public Scope scope() {
        return Scope.SINGLETON;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(BeanDescriptor<T> descriptor, ArcContainer container) {
        return (T) instances.computeIfAbsent(descriptor.beanClass(), k -> {
            T instance = descriptor.factory().create(container);
            invokePostConstruct(instance, descriptor);
            return instance;
        });
    }

    @Override
    public void destroy() {
        for (Object instance : instances.values()) {
            invokePreDestroy(instance);
        }
        instances.clear();
    }

    @Override
    public boolean isActive() {
        return true; // Singleton context is always active
    }

    private void invokePostConstruct(Object instance, BeanDescriptor<?> descriptor) {
        if (descriptor.postConstructMethod() != null) {
            try {
                Method method = instance.getClass().getMethod(descriptor.postConstructMethod());
                method.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke @PostConstruct method", e);
            }
        }
    }

    private void invokePreDestroy(Object instance) {
        // We need to find the descriptor for this instance to get the preDestroy method
        // For now, we'll search for @PreDestroy annotated methods
        for (Method method : instance.getClass().getMethods()) {
            if (method.isAnnotationPresent(com.tanwir.arc.PreDestroy.class)) {
                try {
                    method.invoke(instance);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke @PreDestroy method", e);
                }
            }
        }
    }
}
