package com.tanwir.arc;

public interface InstanceHandle<T> extends AutoCloseable {

    T get();

    @Override
    default void close() {
        // No-op for the initial singleton-only implementation.
    }
}
