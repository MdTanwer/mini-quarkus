package com.tanwir.arc;

@FunctionalInterface
public interface BeanFactory<T> {

    T create(ArcContainer container);
}
