package com.tanwir.resteasy.reactive.server;

import com.tanwir.arc.Arc;
import com.tanwir.arc.ArcContainer;
import com.tanwir.bootstrap.model.MiniApplicationModel;

import java.util.ServiceLoader;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
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
        private int routeCount;

        private VertxRouteRegistrar(Router router, ArcContainer arcContainer) {
            this.router = router;
            this.arcContainer = arcContainer;
        }

        @Override
        public <T> void registerGet(String path, String operationId, Class<T> resourceClass, GetInvoker<T> invoker) {
            routeCount++;
            LOG.infof("Registered GET %s -> %s", path, operationId);
            router.get(path).handler(routingContext -> {
                String requestPath = routingContext.request().path();
                LOG.infof("GET %s received", requestPath);
                LOG.infof("matched route -> %s", operationId);
                try {
                    T resource = arcContainer.instance(resourceClass).get();
                    LOG.infof("resolved bean -> %s", resourceClass.getName());
                    String body = String.valueOf(invoker.invoke(resource));
                    LOG.infof("invoked method -> returned \"%s\"", body);
                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "text/plain")
                            .end(body);
                    LOG.info("response sent -> 200");
                } catch (Throwable throwable) {
                    LOG.errorf(throwable, "GET %s failed", requestPath);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "text/plain")
                            .end("Internal Server Error");
                }
            });
        }

        private int routeCount() {
            return routeCount;
        }
    }
}
