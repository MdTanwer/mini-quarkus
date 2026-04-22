package com.tanwir.arc.deployment;

import com.tanwir.arc.ArcContainer;
import com.tanwir.core.deployment.RuntimeValue;
import com.tanwir.core.deployment.SimpleBuildItem;

/**
 * Signals that the ARC CDI container has been initialized and is ready.
 *
 * <p>Mirrors {@code io.quarkus.arc.deployment.BeanContainerBuildItem} from real Quarkus.
 * Produced exactly once by {@link ArcRecorder#initContainer} and consumed by any
 * deployment processor that needs to interact with the CDI container.
 *
 * <p>Because this is a {@link SimpleBuildItem}, only one instance exists per build.
 * Build steps that declare it as a parameter are deferred until ARC is ready.
 */
public final class BeanContainerBuildItem extends SimpleBuildItem {

    private final RuntimeValue<ArcContainer> container;

    public BeanContainerBuildItem(RuntimeValue<ArcContainer> container) {
        this.container = container;
    }

    /** The live ARC container, wrapped in a {@link RuntimeValue}. */
    public RuntimeValue<ArcContainer> container() {
        return container;
    }

    /** Convenience accessor — unwraps the {@link RuntimeValue}. */
    public ArcContainer get() {
        return container.getValue();
    }
}
