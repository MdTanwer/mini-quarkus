package com.tanwir.arc.context;

import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.BeanDescriptor;
import com.tanwir.arc.Scope;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RequestContext implements ScopeContext {
    private final ThreadLocal<Map<Class<?>, ContextInstance>> requestInstances = new ThreadLocal<>();

    @Override
    public Scope scope() {
        return Scope.REQUEST;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(BeanDescriptor<T> descriptor, ArcContainer container) {
        if (!isActive()) {
            throw new IllegalStateException("Request context is not active");
        }
        return (T) requestInstances.get().computeIfAbsent(descriptor.beanClass(), k -> {
            T instance = descriptor.factory().create(container);
            invokePostConstruct(instance, descriptor);
            return new ContextInstance(descriptor, instance);
        }).instance();
    }

    @Override
    public void destroy() {
        Map<Class<?>, ContextInstance> instances = requestInstances.get();
        if (instances == null) {
            return;
        }
        for (ContextInstance contextInstance : instances.values()) {
            invokePreDestroy(contextInstance.instance(), contextInstance.descriptor());
        }
        instances.clear();
        requestInstances.remove();
    }

    @Override
    public boolean isActive() {
        return requestInstances.get() != null;
    }

    public void activate() {
        if (!isActive()) {
            requestInstances.set(new ConcurrentHashMap<>());
        }
    }

    public void deactivate() {
        destroy();
    }

    private void invokePostConstruct(Object instance, BeanDescriptor<?> descriptor) {
        if (descriptor.postConstructMethod() != null) {
            try {
                Method method = instance.getClass().getMethod(descriptor.postConstructMethod());
                method.setAccessible(true);
                method.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke @PostConstruct method", e);
            }
        }
    }

    private void invokePreDestroy(Object instance, BeanDescriptor<?> descriptor) {
        if (descriptor.preDestroyMethod() == null) {
            return;
        }
        try {
            Method method = instance.getClass().getMethod(descriptor.preDestroyMethod());
            method.setAccessible(true);
            method.invoke(instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke @PreDestroy method", e);
        }
    }

    private record ContextInstance(BeanDescriptor<?> descriptor, Object instance) {
    }
}
