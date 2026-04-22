package com.tanwir.resteasy.reactive.deployment;

import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.deployment.BeanContainerBuildItem;
import com.tanwir.core.deployment.Recorder;
import com.tanwir.core.deployment.RuntimeValue;
import com.tanwir.resteasy.reactive.server.MiniResteasyReactiveServer;

import org.jboss.logging.Logger;

/**
 * Recorder that initializes the HTTP server at deployment time.
 *
 * <p>Mirrors {@code io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveRecorder}
 * from real Quarkus. The recorder receives the ARC container (via
 * {@link BeanContainerBuildItem}) and starts the HTTP server so all routes can be
 * registered before the first request arrives.
 *
 * <p>In real Quarkus, the recorder's method call is encoded as bytecode at build time
 * and replayed during {@code STATIC_INIT}. In mini-quarkus, it runs directly during
 * the deployment phase.
 */
@Recorder
public class ResteasyReactiveRecorder {

    private static final Logger LOG = Logger.getLogger(ResteasyReactiveRecorder.class);

    /**
     * Starts the HTTP server on the given port.
     *
     * <p>Delegates to {@link MiniResteasyReactiveServer#start(int, ArcContainer)} which
     * discovers all generated route registrars via {@link java.util.ServiceLoader} —
     * the same ServiceLoader pattern used throughout the mini-quarkus extension framework.
     *
     * @param container the initialized ARC container (routes are served by CDI beans)
     * @param port      the HTTP port to listen on
     * @return a {@link RuntimeValue} wrapping the running server
     */
    public RuntimeValue<MiniResteasyReactiveServer> startHttpServer(
            ArcContainer container, int port) {
        LOG.infof("[deployment] ResteasyReactiveRecorder: starting HTTP server on port %d", port);
        MiniResteasyReactiveServer server = MiniResteasyReactiveServer.start(port, container);
        return new RuntimeValue<>(server);
    }
}
