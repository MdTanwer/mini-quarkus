package com.tanwir.arc;

public final class Arc {

    private static volatile ArcContainer container;

    private Arc() {
    }

    public static ArcContainer initialize(Class<?>... beanTypes) {
        ArcContainer initialized = new SimpleArcContainer(beanTypes);
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
