package com.tanwir.arc;

import java.util.Set;

public final class BeanDescriptor<T> {
    private final Class<T> beanClass;
    private final Scope scope;
    private final BeanFactory<T> factory;
    private final String postConstructMethod;  // null if none
    private final String preDestroyMethod;     // null if none
    private final Set<Class<?>> qualifiers;    // empty if none

    public BeanDescriptor(Class<T> beanClass, Scope scope, BeanFactory<T> factory, 
                          String postConstructMethod, String preDestroyMethod, 
                          Set<Class<?>> qualifiers) {
        this.beanClass = beanClass;
        this.scope = scope;
        this.factory = factory;
        this.postConstructMethod = postConstructMethod;
        this.preDestroyMethod = preDestroyMethod;
        this.qualifiers = qualifiers;
    }

    public Class<T> beanClass() {
        return beanClass;
    }

    public Scope scope() {
        return scope;
    }

    public BeanFactory<T> factory() {
        return factory;
    }

    public String postConstructMethod() {
        return postConstructMethod;
    }

    public String preDestroyMethod() {
        return preDestroyMethod;
    }

    public Set<Class<?>> qualifiers() {
        return qualifiers;
    }
}
