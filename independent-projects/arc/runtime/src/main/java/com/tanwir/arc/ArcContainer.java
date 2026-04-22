package com.tanwir.arc;

import com.tanwir.arc.context.RequestContextController;

import java.lang.annotation.Annotation;

public interface ArcContainer {

    <T> InstanceHandle<T> instance(Class<T> type);

    <T> InstanceHandle<T> instance(Class<T> type, Class<? extends Annotation> qualifier);

    /** Returns {@code true} if a bean of the given type is registered in this container. */
    boolean isRegistered(Class<?> type);

    /**
     * Registers an additional bean descriptor at runtime.
     *
     * <p>Used by infrastructure code (e.g. the server runtime) to make framework-managed
     * objects — such as {@code MiniEventBus} — injectable into application beans.
     * Mirrors the concept of Quarkus's synthetic bean registration done by extension recorders.
     *
     * @throws IllegalStateException if a bean for the same type is already registered
     */
    <T> void registerBean(BeanDescriptor<T> descriptor);

    RequestContextController requestContextController();
}
