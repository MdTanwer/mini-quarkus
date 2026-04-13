package com.tanwir.arc;

import com.tanwir.arc.context.RequestContextController;

import java.lang.annotation.Annotation;

public interface ArcContainer {

    <T> InstanceHandle<T> instance(Class<T> type);
    
    <T> InstanceHandle<T> instance(Class<T> type, Class<? extends Annotation> qualifier);
    
    RequestContextController requestContextController();
}
