package com.tanwir.resteasy.reactive.server;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import java.util.Map;

@FunctionalInterface
public interface MethodInvoker<T> {

    Object invoke(T resource, HttpServerRequest request, Map<String, String> pathParams, Map<String, String> queryParams, JsonObject body);
}
