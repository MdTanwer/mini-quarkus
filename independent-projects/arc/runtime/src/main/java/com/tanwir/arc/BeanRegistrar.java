package com.tanwir.arc;

public interface BeanRegistrar {

    <T> void register(BeanDescriptor<T> descriptor);
}
