package com.tanwir.arc;

import java.util.ServiceLoader;

public final class Arc {

    private static volatile ArcContainer container;

    private Arc() {
    }

    public static ArcContainer initialize() {
        SimpleArcContainer initialized = new SimpleArcContainer();
        int registrars = 0;
        for (GeneratedBeanRegistrar registrar : ServiceLoader.load(GeneratedBeanRegistrar.class)) {
            registrar.register(initialized);
            registrars++;
        }
        if (registrars == 0) {
            throw new IllegalStateException(
                    "No generated bean registrars found. Add mini-quarkus-arc-processor during compilation.");
        }
        container = initialized;
        return initialized;
    }

    public static ArcContainer initialize(GeneratedBeanRegistrar... registrars) {
        if (registrars.length == 0) {
            throw new IllegalArgumentException("At least one generated bean registrar is required");
        }
        SimpleArcContainer initialized = new SimpleArcContainer();
        for (GeneratedBeanRegistrar registrar : registrars) {
            registrar.register(initialized);
        }
        container = initialized;
        return initialized;
    }

    public static ArcContainer initialize(Class<?>... beanTypes) {
        SimpleArcContainer initialized = new SimpleArcContainer();
        for (Class<?> beanType : beanTypes) {
            initialized.registerReflectiveSingleton(beanType);
        }
        container = initialized;
        return initialized;
    }

    public static ArcContainer container() {
        if (container == null) {
            throw new IllegalStateException("Arc container has not been initialized");
        }
        return container;
    }

    public static void shutdown() {
        container = null;
    }
}
