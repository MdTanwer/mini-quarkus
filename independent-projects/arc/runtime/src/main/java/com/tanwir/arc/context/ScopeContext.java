package com.tanwir.arc.context;

import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.BeanDescriptor;
import com.tanwir.arc.Scope;

public interface ScopeContext {
    Scope scope();
    <T> T getOrCreate(BeanDescriptor<T> descriptor, ArcContainer container);
    void destroy();   // called on scope end — triggers @PreDestroy
    boolean isActive(); 
}
