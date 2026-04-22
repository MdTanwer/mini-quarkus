package com.tanwir.mutiny.deployment;

import com.tanwir.arc.Arc;
import com.tanwir.arc.deployment.SyntheticBeanBuildItem;
import com.tanwir.core.deployment.BuildProducer;
import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.mutiny.MiniEventBus;
import com.tanwir.mutiny.MiniMutinyInfrastructure;

import io.vertx.core.Vertx;

import org.jboss.logging.Logger;

/**
 * Deployment processor for the Mutiny reactive extension.
 *
 * <p>Mirrors {@code io.quarkus.mutiny.deployment.MutinyProcessor} from real Quarkus.
 * Its primary responsibility is to expose {@link MiniEventBus} as a synthetic CDI bean
 * so application code can {@code @Inject MiniEventBus} without having to know how it
 * was created.
 *
 * <h2>Key insight: the SyntheticBeanBuildItem pattern</h2>
 * Instead of annotating {@code MiniEventBus} with {@code @Singleton} (which would require
 * it to have a no-arg constructor), the deployment processor produces a
 * {@link SyntheticBeanBuildItem} that supplies the pre-created instance. The ARC recorder
 * then registers this synthetic bean with the container.
 *
 * <p>This is exactly how real Quarkus makes framework-managed objects injectable:
 * Vert.x, the HTTP client, the Kafka producer, Redis client — all registered as
 * synthetic beans via their respective deployment processors.
 */
public class MutinyDeploymentProcessor implements ExtensionProcessor {

    private static final Logger LOG = Logger.getLogger(MutinyDeploymentProcessor.class);

    @BuildStep
    public void announceFeature(BuildProducer<FeatureBuildItem> features) {
        features.produce(new FeatureBuildItem("mutiny"));
        LOG.debug("[deployment] Mutiny feature announced");
    }

    /**
     * Produces a {@link SyntheticBeanBuildItem} so {@link MiniEventBus} is injectable.
     *
     * <h2>Cycle-free design</h2>
     * This step does NOT depend on {@link com.tanwir.arc.deployment.BeanContainerBuildItem}.
     * Instead it creates the Vert.x instance and Mutiny infrastructure eagerly, but wraps
     * the {@link MiniEventBus} creation in a <em>lazy supplier</em> that calls
     * {@link Arc#container()} when first accessed. By the time any bean requests
     * {@code MiniEventBus}, the ARC container is already initialized.
     *
     * <p>This mirrors real Quarkus: {@code SyntheticBeanBuildItem}s are produced
     * <em>before</em> the container is built, and their suppliers execute lazily
     * <em>after</em> the container is ready.
     *
     * <p>Real Quarkus analogue: {@code VertxCoreProcessor} produces
     * {@code SyntheticBeanBuildItem} for {@code io.vertx.mutiny.core.eventbus.EventBus}.
     */
    @BuildStep
    public SyntheticBeanBuildItem exposeEventBus() {
        LOG.info("[deployment] MutinyDeploymentProcessor: registering MiniEventBus as synthetic bean");

        // Create Vert.x and configure Mutiny infrastructure eagerly at deployment time
        Vertx vertx = Vertx.vertx();
        MiniMutinyInfrastructure.initialize(vertx);

        // The supplier is invoked lazily when the first @Inject MiniEventBus is resolved.
        // At that point Arc.container() is guaranteed to be initialized.
        return SyntheticBeanBuildItem.configure(MiniEventBus.class)
                .supplier(() -> MiniEventBus.initialize(vertx, Arc.container()))
                .build();
    }
}
