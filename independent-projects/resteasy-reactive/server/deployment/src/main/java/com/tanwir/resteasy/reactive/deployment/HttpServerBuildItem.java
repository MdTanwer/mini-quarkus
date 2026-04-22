package com.tanwir.resteasy.reactive.deployment;

import com.tanwir.core.deployment.RuntimeValue;
import com.tanwir.core.deployment.SimpleBuildItem;
import com.tanwir.resteasy.reactive.server.MiniResteasyReactiveServer;

/**
 * Signals that the HTTP server has been configured and is ready to start.
 *
 * <p>Mirrors the concept of {@code io.quarkus.vertx.http.deployment.HttpBuildTimeConfig}
 * and {@code VertxHttpRecorder.HttpStartedBuildItem} from real Quarkus. Wraps the
 * {@link MiniResteasyReactiveServer} instance created by the recorder.
 */
public final class HttpServerBuildItem extends SimpleBuildItem {

    private final RuntimeValue<MiniResteasyReactiveServer> server;

    public HttpServerBuildItem(RuntimeValue<MiniResteasyReactiveServer> server) {
        this.server = server;
    }

    public RuntimeValue<MiniResteasyReactiveServer> server() {
        return server;
    }

    public MiniResteasyReactiveServer get() {
        return server.getValue();
    }
}
