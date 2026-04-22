package com.tanwir.resteasy.reactive.deployment;

import com.tanwir.core.deployment.MultiBuildItem;

/**
 * Represents a REST route discovered during the deployment phase.
 *
 * <p>Mirrors {@code io.quarkus.resteasy.reactive.server.deployment.RouteBuildItem}
 * from real Quarkus. One instance is produced for each {@code @GET}, {@code @POST}, etc.
 * method found in the application.
 *
 * <p>Consumed by {@link ResteasyReactiveDeploymentProcessor#setupRoutes} which passes
 * the route information to the recorder for HTTP server configuration.
 */
public final class RouteBuildItem extends MultiBuildItem {

    private final String httpMethod;
    private final String path;
    private final String operationId;
    private final String resourceClass;

    public RouteBuildItem(String httpMethod, String path, String operationId, String resourceClass) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.operationId = operationId;
        this.resourceClass = resourceClass;
    }

    public String httpMethod() { return httpMethod; }
    public String path()       { return path; }
    public String operationId(){ return operationId; }
    public String resourceClass(){ return resourceClass; }

    @Override
    public String toString() {
        return "RouteBuildItem[" + httpMethod + " " + path + " -> " + operationId + "]";
    }
}
