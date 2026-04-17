package com.tanwir.arc.context;

public class RequestContextController {
    private final RequestContext requestContext;

    public RequestContextController(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public void activate() {
        requestContext.activate();
    }

    public void deactivate() {
        requestContext.deactivate();
    }
}
