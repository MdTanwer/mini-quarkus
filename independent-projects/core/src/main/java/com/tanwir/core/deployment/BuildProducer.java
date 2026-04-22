package com.tanwir.core.deployment;

/**
 * Injected into {@link BuildStep} methods to produce {@link MultiBuildItem}s.
 *
 * <p>Mirrors {@code io.quarkus.deployment.BuildProducer} from real Quarkus.
 * A build step that needs to produce multiple items of the same type (or produce
 * items conditionally) uses {@code BuildProducer} instead of a return value:
 *
 * <pre>{@code
 * @BuildStep
 * public void discoverBeans(
 *         BuildProducer<BeanBuildItem> beans,
 *         BuildProducer<FeatureBuildItem> features) {
 *     features.produce(new FeatureBuildItem("arc"));
 *     for (Class<?> cls : scanForAnnotations(Singleton.class)) {
 *         beans.produce(new BeanBuildItem(cls));
 *     }
 * }
 * }</pre>
 *
 * <p>The {@link MiniExtensionManager} injects a concrete implementation backed by
 * the central {@link BuildContext}.
 *
 * @param <T> the type of {@link BuildItem} produced
 */
@FunctionalInterface
public interface BuildProducer<T extends BuildItem> {

    /**
     * Adds {@code item} to the build context.
     * May be called zero or more times within a single build step.
     */
    void produce(T item);
}
