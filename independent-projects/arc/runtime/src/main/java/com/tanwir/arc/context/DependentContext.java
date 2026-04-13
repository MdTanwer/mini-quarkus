package com.tanwir.arc.context;

import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.BeanDescriptor;
import com.tanwir.arc.Scope;

import java.lang.reflect.Method;

public class DependentContext implements ScopeContext {
    @Override
    public Scope scope() {
        return Scope.DEPENDENT;
    }

    @Override
    public <T> T getOrCreate(BeanDescriptor<T> descriptor, ArcContainer container) {
        T instance = descriptor.factory().create(container);
        invokePostConstruct(instance, descriptor);
        return instance;
    }

    @Override
    public void destroy() {
        // Dependent context doesn't store instances, so nothing to destroy
    }

    @Override
    public boolean isActive() {
        return true; // Dependent context is always active
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

    public void destroyInstance(Object instance) {
        invokePreDestroy(instance);
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
