package com.tanwir.resteasy.reactive.server;

import com.tanwir.arc.Arc;
import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.BeanDescriptor;
import com.tanwir.arc.Scope;
import com.tanwir.arc.context.RequestContextController;
import com.tanwir.bootstrap.model.MiniApplicationModel;
import com.tanwir.mutiny.MiniEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Mini Quarkus HTTP server wired to Vert.x.
 *
 * <p>Mirrors the role of {@code io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveRecorder}
 * in real Quarkus — it creates the Vert.x Router, discovers all generated route registrars via
 * {@link ServiceLoader}, and handles both synchronous and reactive ({@link Uni}/{@link Multi})
 * return types from resource methods.
 *
 * <h2>Reactive return types</h2>
 * When a resource method returns {@link Uni}{@code <T>}, the routing layer subscribes to the
 * {@code Uni} without blocking the Vert.x event loop. The request context remains active
 * until the {@code Uni} resolves (success or failure) — exactly like real Quarkus RESTEasy Reactive.
 *
 * <p>When a method returns {@link Multi}{@code <T>}, all items are collected into a list and
 * serialised as a JSON array — a simplified version of Quarkus's SSE/chunked-transfer support.
 */
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
            LOG.infof("application model route -> %s (%s)", route.path(), route.operationId());
        }
        return start(port, Arc.initialize(), true);
    }

    public static MiniResteasyReactiveServer start(int port, ArcContainer arcContainer) {
        return start(port, arcContainer, false);
    }

    private static MiniResteasyReactiveServer start(int port, ArcContainer arcContainer, boolean ownsArcContainer) {
        Vertx vertx = Vertx.vertx();

        // Wire Mutiny to Vert.x event-loop and register @ConsumeEvent handlers.
        // In Phase 6 MutinyDeploymentProcessor registers MiniEventBus as a synthetic bean
        // BEFORE the server starts, so we only register it here if it isn't already present
        // (preserves backward-compat with the Phase 5 standalone start() overload).
        MiniEventBus eventBus;
        if (arcContainer.isRegistered(MiniEventBus.class)) {
            // Phase 6: MutinyDeploymentProcessor already registered it as a synthetic bean
            eventBus = arcContainer.instance(MiniEventBus.class).get();
        } else {
            // Phase 5 standalone path: register it here
            eventBus = MiniEventBus.initialize(vertx, arcContainer);
            arcContainer.registerBean(new BeanDescriptor<>(
                    MiniEventBus.class, Scope.SINGLETON, c -> eventBus, null, null, java.util.Set.of()));
        }

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
                    "No generated routes found. Add mini-quarkus-resteasy-reactive-processor during compilation.");
        }

        HttpServer server = vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .toCompletionStage()
                .toCompletableFuture()
                .join();

        LOG.infof("HTTP server listening on port %d with %d route(s)", server.actualPort(), registrar.routeCount());
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

    // -------------------------------------------------------------------------
    // Inner registrar — maps RouteRegistrar calls to Vert.x Router
    // -------------------------------------------------------------------------

    private static final class VertxRouteRegistrar implements RouteRegistrar {

        private final Router router;
        private final ArcContainer arcContainer;
        private final ObjectMapper objectMapper;
        private int routeCount;

        private VertxRouteRegistrar(Router router, ArcContainer arcContainer) {
            this.router = router;
            this.arcContainer = arcContainer;
            this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
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

        private <T> void registerRoute(
                String httpMethod,
                String path,
                String operationId,
                Class<T> resourceClass,
                MethodInvoker<T> invoker,
                java.util.function.Function<String, io.vertx.ext.web.Route> routeMethod) {

            routeCount++;
            LOG.infof("Registered %s %s -> %s", httpMethod, path, operationId);

            // Vert.x uses colon-style (:id) but JAX-RS uses brace-style ({id}).
            // Convert before passing to the router — mirrors Quarkus's VertxHttpRecorder
            // which does the same path-parameter syntax translation.
            String vertxPath = path.replaceAll("\\{([^}]+)}", ":$1");

            routeMethod.apply(vertxPath).handler(routingContext -> {
                String requestPath = routingContext.request().path();
                LOG.infof("%s %s received", httpMethod, requestPath);

                Map<String, String> pathParams = new HashMap<>();
                routingContext.pathParams().forEach(pathParams::put);

                Map<String, String> queryParams = new HashMap<>();
                routingContext.queryParams().forEach(e -> queryParams.put(e.getKey(), e.getValue()));

                JsonObject body = null;
                String bodyStr = routingContext.getBodyAsString();
                if (bodyStr != null && !bodyStr.isEmpty()) {
                    body = routingContext.getBodyAsJson();
                }

                RequestContextController rcc = arcContainer.requestContextController();
                rcc.activate();

                // asyncMode prevents the finally block from deactivating the request context
                // prematurely when the handler returns a Uni/Multi — mirroring how real Quarkus
                // RESTEasy Reactive keeps the context alive until the async pipeline settles.
                boolean[] asyncMode = { false };
                try {
                    T resource = arcContainer.instance(resourceClass).get();
                    Object result = invoker.invoke(resource, routingContext.request(), pathParams, queryParams, body);
                    LOG.infof("invoked %s -> %s", operationId,
                            result != null ? result.getClass().getSimpleName() : "null");

                    if (result instanceof Uni<?> uni) {
                        // ---- Async / reactive path ----
                        asyncMode[0] = true;
                        // The request context will be deactivated inside the subscribe callbacks.
                        final RequestContextController capturedRcc = rcc;
                        uni.subscribe().with(
                                item -> {
                                    try {
                                        sendResult(routingContext, item);
                                    } finally {
                                        capturedRcc.deactivate();
                                    }
                                },
                                failure -> {
                                    try {
                                        sendError(routingContext, httpMethod, requestPath, failure);
                                    } finally {
                                        capturedRcc.deactivate();
                                    }
                                });

                    } else if (result instanceof Multi<?> multi) {
                        // ---- Collect Multi items then respond (simplified; real Quarkus streams) ----
                        asyncMode[0] = true;
                        final RequestContextController capturedRcc = rcc;
                        multi.collect().asList().subscribe().with(
                                items -> {
                                    try {
                                        sendResult(routingContext, items);
                                    } finally {
                                        capturedRcc.deactivate();
                                    }
                                },
                                failure -> {
                                    try {
                                        sendError(routingContext, httpMethod, requestPath, failure);
                                    } finally {
                                        capturedRcc.deactivate();
                                    }
                                });

                    } else {
                        // ---- Synchronous path ----
                        sendResult(routingContext, result);
                    }

                } catch (Throwable t) {
                    sendError(routingContext, httpMethod, requestPath, t);
                } finally {
                    if (!asyncMode[0]) {
                        rcc.deactivate();
                    }
                }
            });
        }

        private void sendResult(io.vertx.ext.web.RoutingContext rc, Object result) {
            if (result instanceof Response response) {
                sendResponse(rc, response.getStatus(), response.getEntity(), response.getContentType());
            } else if (result instanceof String s) {
                sendResponse(rc, 200, s, "text/plain");
            } else if (result != null) {
                sendResponse(rc, 200, result, "application/json");
            } else {
                sendResponse(rc, 204, null, null);
            }
            LOG.infof("response sent -> %d", result instanceof Response r ? r.getStatus() : (result == null ? 204 : 200));
        }

        private void sendError(io.vertx.ext.web.RoutingContext rc, String method, String path, Throwable t) {
            LOG.errorf(t, "%s %s failed", method, path);
            String msg = t.getMessage() != null ? t.getMessage().replace("\"", "'") : "Unknown error";
            rc.response()
                    .setStatusCode(500)
                    .putHeader("content-type", "application/json")
                    .end("{\"error\":\"Internal Server Error\",\"message\":\"" + msg + "\"}");
        }

        private void sendResponse(io.vertx.ext.web.RoutingContext rc, int status, Object entity, String contentType) {
            if (entity == null) {
                rc.response().setStatusCode(status).end();
                return;
            }
            String body;
            if (entity instanceof String s) {
                body = s;
            } else {
                try {
                    body = objectMapper.writeValueAsString(entity);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to serialise response entity");
                    rc.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json")
                            .end("{\"error\":\"Serialization Error\"}");
                    return;
                }
            }
            rc.response()
                    .setStatusCode(status)
                    .putHeader("content-type", contentType != null ? contentType : "application/json")
                    .end(body);
        }

        private int routeCount() {
            return routeCount;
        }
    }
}
