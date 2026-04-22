package com.tanwir.bootstrap.runner;

import com.tanwir.bootstrap.model.MiniApplicationModel;
import com.tanwir.bootstrap.model.MiniApplicationModelLoader;
import com.tanwir.core.deployment.MiniExtensionManager;
import com.tanwir.resteasy.reactive.deployment.HttpServerBuildItem;
import com.tanwir.resteasy.reactive.server.MiniResteasyReactiveServer;

import java.util.concurrent.CountDownLatch;

import org.jboss.logging.Logger;

/**
 * Application entry point — orchestrates the two-phase Quarkus startup model.
 *
 * <h2>Phase 1: Deployment (build-time augmentation)</h2>
 * {@link MiniExtensionManager#runBuildSteps()} discovers all {@link com.tanwir.core.deployment.ExtensionProcessor}
 * implementations via {@link java.util.ServiceLoader} and executes their
 * {@link com.tanwir.core.deployment.BuildStep} methods in dependency order:
 * <ol>
 *   <li>{@code ArcDeploymentProcessor.build} — initializes the CDI container, producing
 *       a {@link com.tanwir.arc.deployment.BeanContainerBuildItem}</li>
 *   <li>{@code MutinyDeploymentProcessor.exposeEventBus} — creates {@code MiniEventBus},
 *       registers it as a synthetic bean so it's injectable</li>
 *   <li>{@code ResteasyReactiveDeploymentProcessor.setupHttpServer} — starts the HTTP
 *       server (requires the container from step 1), producing an {@link HttpServerBuildItem}</li>
 * </ol>
 *
 * <h2>Phase 2: Runtime</h2>
 * Waits for a shutdown signal. The HTTP server (started in Phase 1 by the recorder) is
 * already accepting connections at this point.
 *
 * <h2>Real Quarkus comparison</h2>
 * In real Quarkus Phase 1 happens at Maven build time (not startup time). The recorded
 * startup code is stored as bytecode in the JAR and replayed by
 * {@code io.quarkus.runner.ApplicationImpl} at JVM startup. In mini-quarkus both phases
 * run at JVM startup — same ordering, same contracts, but without the build-time/startup
 * split that enables GraalVM native compilation.
 */
public final class MiniQuarkusEntryPoint {

    private static final Logger LOG = Logger.getLogger(MiniQuarkusEntryPoint.class);

    private MiniQuarkusEntryPoint() {
    }

    public static void main(String[] args) throws InterruptedException {
        long startMs = System.currentTimeMillis();

        // ---- Log application model (produced by annotation processors at compile time) ----
        MiniApplicationModel applicationModel = MiniApplicationModelLoader.load();
        LOG.infof("Application: %s  |  %d compile-time route(s) in model",
                applicationModel.applicationName(), applicationModel.getRoutes().size());

        // ---- Phase 1: Deployment — run all @BuildStep methods ----
        // This is where real Quarkus does its build-time augmentation.
        // In mini-quarkus it runs at startup; the result is identical.
        LOG.info("=== Deployment phase starting ===");
        MiniExtensionManager manager = new MiniExtensionManager();
        manager.runBuildSteps();
        LOG.info("=== Deployment phase complete ===");

        // ---- Phase 2: Runtime — server is already running (started by recorder) ----
        HttpServerBuildItem httpServer = manager.getSimpleItem(HttpServerBuildItem.class);
        if (httpServer == null) {
            throw new IllegalStateException(
                    "HTTP server was not started. Ensure mini-quarkus-resteasy-reactive-deployment "
                    + "is on the classpath.");
        }

        MiniResteasyReactiveServer server = httpServer.get();
        long startedMs = System.currentTimeMillis() - startMs;
        System.out.printf("%n=== mini-quarkus started in %d ms ===%n", startedMs);
        System.out.printf("Listening on: http://localhost:%d%n", server.port());
        System.out.printf("Active features: %s%n%n",
                manager.getMultiItems(MiniExtensionManager.FeatureBuildItem.class)
                        .stream().map(MiniExtensionManager.FeatureBuildItem::name).toList());

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "mini-quarkus-shutdown"));
        try {
            shutdown.await();
        } finally {
            server.close();
        }
    }
}
