package com.tanwir.arc.deployment;

import com.tanwir.core.deployment.BuildProducer;
import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.core.deployment.Record;
import com.tanwir.core.deployment.ExecutionTime;

import org.jboss.logging.Logger;

import java.util.List;

/**
 * Deployment processor for the ARC CDI extension.
 *
 * <p>Mirrors {@code io.quarkus.arc.deployment.ArcProcessor} from real Quarkus.
 * Contains {@link BuildStep} methods that:
 * <ol>
 *   <li>Announce the ARC feature</li>
 *   <li>Initialize the ARC container via the {@link ArcRecorder}</li>
 *   <li>Produce a {@link BeanContainerBuildItem} consumed by other extension processors</li>
 * </ol>
 *
 * <p>Registered as an {@link ExtensionProcessor} in
 * {@code META-INF/services/com.tanwir.core.deployment.ExtensionProcessor}.
 *
 * <h2>Real Quarkus comparison</h2>
 * <table>
 *   <tr><th>mini-quarkus</th><th>real Quarkus (arc-deployment)</th></tr>
 *   <tr><td>{@link #announceFeature}</td>
 *       <td>{@code ArcProcessor.registerFeature()}</td></tr>
 *   <tr><td>{@link #build}</td>
 *       <td>{@code ArcProcessor.build(ArcRecorder, ...)}</td></tr>
 * </table>
 */
public class ArcDeploymentProcessor implements ExtensionProcessor {

    private static final Logger LOG = Logger.getLogger(ArcDeploymentProcessor.class);

    /**
     * Announces that the ARC CDI feature is active.
     * Mirrors {@code ArcProcessor.registerFeature()} in real Quarkus.
     */
    @BuildStep
    public void announceFeature(BuildProducer<FeatureBuildItem> features) {
        features.produce(new FeatureBuildItem("arc"));
        LOG.debug("[deployment] ARC feature announced");
    }

    /**
     * Initializes the ARC container and produces a {@link BeanContainerBuildItem}.
     *
     * <p>The {@link ArcRecorder} is injected by the {@link com.tanwir.core.deployment.MiniExtensionManager}
     * because it is annotated with {@link com.tanwir.core.deployment.Recorder}.
     * All {@link SyntheticBeanBuildItem}s produced by other steps are passed to the
     * recorder so the container can make them injectable.
     *
     * <p>Mirrors:
     * <pre>{@code
     * // Real Quarkus
     * @BuildStep
     * @Record(ExecutionTime.STATIC_INIT)
     * public BeanContainerBuildItem build(ArcRecorder recorder,
     *         List<AdditionalBeanBuildItem> additionalBeans, ...) { ... }
     * }</pre>
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public BeanContainerBuildItem build(
            ArcRecorder recorder,
            List<SyntheticBeanBuildItem> syntheticBeans) {

        LOG.infof("[deployment] ArcDeploymentProcessor.build — %d synthetic bean(s)", syntheticBeans.size());
        return new BeanContainerBuildItem(recorder.initContainer(syntheticBeans));
    }
}
