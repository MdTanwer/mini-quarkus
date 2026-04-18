package com.tanwir.resteasy.reactive.server;

public interface ExceptionMapper<T extends Throwable> {
    Response toResponse(T exception);
}
