package com.tanwir.arc.context;

public class RequestContextController {
    private final RequestContext requestContext;

    public RequestContextController(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public void activate() {
        // Ensure the thread-local map is initialized
        // This is handled by ThreadLocal.withInitial() in RequestContext
    }

    public void deactivate() {
        requestContext.destroy();
    }
}
