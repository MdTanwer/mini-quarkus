package com.tanwir.resteasy.reactive.deployment;

import com.tanwir.arc.deployment.BeanContainerBuildItem;
import com.tanwir.core.deployment.BuildProducer;
import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.ExecutionTime;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.core.deployment.Record;

import org.jboss.logging.Logger;

/**
 * Deployment processor for the RESTEasy Reactive extension.
 *
 * <p>Mirrors {@code io.quarkus.resteasy.reactive.server.deployment.ResteasyReactiveProcessor}
 * from real Quarkus. Contains {@link BuildStep} methods that:
 * <ol>
 *   <li>Announce the {@code resteasy-reactive} feature</li>
 *   <li>Wait for the ARC container (via {@link BeanContainerBuildItem})</li>
 *   <li>Start the HTTP server via the {@link ResteasyReactiveRecorder}</li>
 *   <li>Produce a {@link HttpServerBuildItem} that signals the server is ready</li>
 * </ol>
 *
 * <h2>Dependency ordering</h2>
 * The {@link #setupHttpServer} step requires a {@link BeanContainerBuildItem}
 * parameter — the {@link com.tanwir.core.deployment.MiniExtensionManager} will
 * defer this step until {@code arc-deployment}'s build step has run and produced
 * the container. This is the core dependency-resolution mechanism in action.
 */
public class ResteasyReactiveDeploymentProcessor implements ExtensionProcessor {

    private static final Logger LOG = Logger.getLogger(ResteasyReactiveDeploymentProcessor.class);

    @BuildStep
    public void announceFeature(BuildProducer<FeatureBuildItem> features) {
        features.produce(new FeatureBuildItem("resteasy-reactive"));
        LOG.debug("[deployment] RESTEasy Reactive feature announced");
    }

    /**
     * Starts the HTTP server once the ARC container is available.
     *
     * <p>The {@link BeanContainerBuildItem} parameter creates a build-step dependency:
     * this method runs AFTER {@link com.tanwir.arc.deployment.ArcDeploymentProcessor#build}
     * completes and produces the container. The manager enforces this automatically.
     *
     * <p>Mirrors the real Quarkus pattern:
     * <pre>{@code
     * @BuildStep
     * @Record(ExecutionTime.RUNTIME_INIT)
     * public void setupRoutes(ResteasyReactiveRecorder recorder,
     *                         BeanContainerBuildItem beanContainer,
     *                         HttpBuildTimeConfig config) { ... }
     * }</pre>
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public HttpServerBuildItem setupHttpServer(
            ResteasyReactiveRecorder recorder,
            BeanContainerBuildItem beanContainer) {

        int port = Integer.getInteger("mini.quarkus.http.port", 8080);
        LOG.infof("[deployment] ResteasyReactiveDeploymentProcessor: configuring HTTP server on port %d", port);

        return new HttpServerBuildItem(
                recorder.startHttpServer(beanContainer.get(), port));
    }
}
