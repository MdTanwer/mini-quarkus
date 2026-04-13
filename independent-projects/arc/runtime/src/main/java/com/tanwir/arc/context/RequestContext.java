package com.tanwir.arc.context;

import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.BeanDescriptor;
import com.tanwir.arc.Scope;

import java.lang.reflect.Method;
import java.util.Map;

public class RequestContext implements ScopeContext {
    private final ThreadLocal<Map<Class<?>, Object>> requestInstances = ThreadLocal.withInitial(() -> new java.util.concurrent.ConcurrentHashMap<>());

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
            return instance;
        });
    }

    @Override
    public void destroy() {
        Map<Class<?>, Object> instances = requestInstances.get();
        for (Object instance : instances.values()) {
            invokePreDestroy(instance);
        }
        instances.clear();
        requestInstances.remove();
    }

    @Override
    public boolean isActive() {
        return requestInstances.get() != null;
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
