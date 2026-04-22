package com.tanwir.core.deployment;

/**
 * A {@link BuildItem} of which exactly one instance may be produced per build.
 *
 * <p>Mirrors {@code io.quarkus.builder.item.SimpleBuildItem}. Use for build items that
 * represent a single global result — for example, "the ARC container is configured" or
 * "the HTTP router is ready". The {@link MiniExtensionManager} enforces uniqueness.
 *
 * <p>A {@link BuildStep} that consumes a {@code SimpleBuildItem} declares it as a direct
 * parameter (not wrapped in {@link java.util.List}):
 *
 * <pre>{@code
 * @BuildStep
 * public void setupRoutes(BeanContainerBuildItem container, ...) { ... }
 * }</pre>
 */
public abstract class SimpleBuildItem extends BuildItem {

    protected SimpleBuildItem() {
    }
}
