package com.tanwir.resteasy.reactive.server;

import com.tanwir.arc.Arc;
import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.context.RequestContextController;
import com.tanwir.bootstrap.model.MiniApplicationModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ServiceLoader;
import java.util.Map;
import java.util.HashMap;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.logging.Logger;

public final class MiniResteasyReactiveServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MiniResteasyReactiveServer.class);

    private final Vertx vertx;
    private final HttpServer server;
    private final boolean ownsArcContainer;

    private MiniResteasyReactiveServer(Vertx vertx, HttpServer server, boolean ownsArcContainer) {
        this.vertx = vertx;
        this.server = server;
        this.ownsArcContainer = ownsArcContainer;
    }

    public static MiniResteasyReactiveServer start(int port) {
        return start(port, Arc.initialize(), true);
    }

    public static MiniResteasyReactiveServer start(MiniApplicationModel applicationModel, int port) {
        LOG.infof("Bootstrapping %s", applicationModel.applicationName());
        for (MiniApplicationModel.RouteModel route : applicationModel.getRoutes()) {
            LOG.infof("application model route -> GET %s (%s)", route.path(), route.operationId());
        }
        return start(port, Arc.initialize(), true);
    }

    public static MiniResteasyReactiveServer start(int port, ArcContainer arcContainer) {
        return start(port, arcContainer, false);
    }

    private static MiniResteasyReactiveServer start(int port, ArcContainer arcContainer, boolean ownsArcContainer) {
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        VertxRouteRegistrar registrar = new VertxRouteRegistrar(router, arcContainer);

        int discoveredRegistrars = 0;
        for (GeneratedRouteRegistrar generatedRegistrar : ServiceLoader.load(GeneratedRouteRegistrar.class)) {
            generatedRegistrar.register(registrar);
            discoveredRegistrars++;
        }

        if (discoveredRegistrars == 0 || registrar.routeCount() == 0) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
            if (ownsArcContainer) {
                Arc.shutdown();
            }
            throw new IllegalStateException(
                    "No generated GET routes found. Add mini-quarkus-resteasy-reactive-processor during compilation.");
        }

        HttpServer server = vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .toCompletionStage()
                .toCompletableFuture()
                .join();

        LOG.infof("HTTP server listening on port %d with %d GET route(s)", server.actualPort(), registrar.routeCount());
        return new MiniResteasyReactiveServer(vertx, server, ownsArcContainer);
    }

    public int port() {
        return server.actualPort();
    }

    @Override
    public void close() {
        vertx.close().toCompletionStage().toCompletableFuture().join();
        if (ownsArcContainer) {
            Arc.shutdown();
        }
    }

    private static final class VertxRouteRegistrar implements RouteRegistrar {

        private final Router router;
        private final ArcContainer arcContainer;
        private final ObjectMapper objectMapper;
        private int routeCount;

        private VertxRouteRegistrar(Router router, ArcContainer arcContainer) {
            this.router = router;
            this.arcContainer = arcContainer;
            this.objectMapper = new ObjectMapper();
            this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        @Override
        public <T> void registerGet(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker) {
            registerRoute("GET", path, operationId, resourceClass, invoker, router::get);
        }

        @Override
        public <T> void registerPost(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker) {
            registerRoute("POST", path, operationId, resourceClass, invoker, router::post);
        }

        @Override
        public <T> void registerPut(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker) {
            registerRoute("PUT", path, operationId, resourceClass, invoker, router::put);
        }

        @Override
        public <T> void registerDelete(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker) {
            registerRoute("DELETE", path, operationId, resourceClass, invoker, router::delete);
        }

        @Override
        public <T> void registerPatch(String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker) {
            registerRoute("PATCH", path, operationId, resourceClass, invoker, router::patch);
        }

        private <T> void registerRoute(String httpMethod, String path, String operationId, Class<T> resourceClass, MethodInvoker<T> invoker, java.util.function.Function<String, io.vertx.ext.web.Route> routeMethod) {
            routeCount++;
            LOG.infof("Registered %s %s -> %s", httpMethod, path, operationId);
            
            routeMethod.apply(path).handler(routingContext -> {
                String requestPath = routingContext.request().path();
                LOG.infof("%s %s received", httpMethod, requestPath);
                LOG.infof("matched route -> %s", operationId);
                
                RequestContextController rcc = arcContainer.requestContextController();
                rcc.activate();
                try {
                    // Extract path parameters
                    Map<String, String> pathParams = new HashMap<>();
                    routingContext.pathParams().forEach(pathParams::put);
                    
                    // Extract query parameters
                    Map<String, String> queryParams = new HashMap<>();
                    routingContext.queryParams().forEach(param -> queryParams.put(param.getKey(), param.getValue()));
                    
                    // Extract request body
                    JsonObject body = null;
                    if (routingContext.getBodyAsString() != null && !routingContext.getBodyAsString().isEmpty()) {
                        body = routingContext.getBodyAsJson();
                    }
                    
                    T resource = arcContainer.instance(resourceClass).get();
                    LOG.infof("resolved bean -> %s", resourceClass.getName());
                    
                    Object result = invoker.invoke(resource, routingContext.request(), pathParams, queryParams, body);
                    LOG.infof("invoked method -> returned %s", result != null ? result.getClass().getSimpleName() : "null");
                    
                    // Handle response
                    if (result instanceof Response) {
                        Response response = (Response) result;
                        sendResponse(routingContext, response.getStatus(), response.getEntity(), response.getContentType());
                    } else if (result instanceof String) {
                        sendResponse(routingContext, 200, result, "text/plain");
                    } else if (result != null) {
                        sendResponse(routingContext, 200, result, "application/json");
                    } else {
                        sendResponse(routingContext, 204, null, null);
                    }
                    
                    LOG.info("response sent -> 200");
                } catch (Throwable throwable) {
                    LOG.errorf(throwable, "%s %s failed", httpMethod, requestPath);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json")
                            .end("{\"error\":\"Internal Server Error\",\"message\":\"" + throwable.getMessage() + "\"}");
                } finally {
                    rcc.deactivate();  // triggers @PreDestroy on all @RequestScoped beans
                }
            });
        }

        private void sendResponse(io.vertx.ext.web.RoutingContext routingContext, int statusCode, Object entity, String contentType) {
            if (entity == null) {
                routingContext.response().setStatusCode(statusCode).end();
                return;
            }
            
            String responseContent;
            if (entity instanceof String) {
                responseContent = (String) entity;
            } else {
                try {
                    responseContent = objectMapper.writeValueAsString(entity);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to serialize response entity");
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json")
                            .end("{\"error\":\"Serialization Error\"}");
                    return;
                }
            }
            
            routingContext.response()
                    .setStatusCode(statusCode)
                    .putHeader("content-type", contentType != null ? contentType : "application/json")
                    .end(responseContent);
        }

        private int routeCount() {
            return routeCount;
        }
    }
}
