package com.tanwir.arc;

public interface BeanRegistrar {

    <T> void register(Class<T> type, BeanFactory<T> factory);
}
