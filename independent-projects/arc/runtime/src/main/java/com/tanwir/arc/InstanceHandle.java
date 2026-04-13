package com.tanwir.arc;

public interface InstanceHandle<T> extends AutoCloseable {

    T get();
    
    @Override
    void close();  // no longer default no-op — implementations handle @PreDestroy
}
