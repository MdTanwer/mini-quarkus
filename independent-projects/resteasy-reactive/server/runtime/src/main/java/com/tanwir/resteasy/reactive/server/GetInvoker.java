package com.tanwir.resteasy.reactive.server;

@FunctionalInterface
public interface GetInvoker<T> {

    String invoke(T resource);
}
