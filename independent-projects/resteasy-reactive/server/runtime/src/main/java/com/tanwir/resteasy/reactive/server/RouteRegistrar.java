package com.tanwir.resteasy.reactive.server;

public interface RouteRegistrar {

    <T> void registerGet(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker);
    <T> void registerPost(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker);
    <T> void registerPut(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker);
    <T> void registerDelete(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker);
    <T> void registerPatch(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker);
}
