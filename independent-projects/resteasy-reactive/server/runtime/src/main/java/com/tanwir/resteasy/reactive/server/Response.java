package com.tanwir.resteasy.reactive.server;

public class Response {
    private final int status;
    private final Object entity;
    private final String contentType;

    private Response(int status, Object entity, String contentType) {
        this.status = status;
        this.entity = entity;
        this.contentType = contentType;
    }

    public static Response ok(Object entity) {
        return new Response(200, entity, "application/json");
    }

    public static Response ok(Object entity, String contentType) {
        return new Response(200, entity, contentType);
    }

    public static Response created(Object entity) {
        return new Response(201, entity, "application/json");
    }

    public static Response noContent() {
        return new Response(204, null, null);
    }

    public static Response status(int status) {
        return new Response(status, null, null);
    }

    public static Response status(int status, Object entity) {
        return new Response(status, entity, "application/json");
    }

    public int getStatus() {
        return status;
    }

    public Object getEntity() {
        return entity;
    }

    public String getContentType() {
        return contentType;
    }
}
