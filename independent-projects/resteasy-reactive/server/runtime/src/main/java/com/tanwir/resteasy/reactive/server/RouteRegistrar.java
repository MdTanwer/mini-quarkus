package com.tanwir.resteasy.reactive.server;

public interface RouteRegistrar {

    <T> void registerGet(String path, String operationId, Class<T> resourceClass, GetInvoker<T> invoker);
}
