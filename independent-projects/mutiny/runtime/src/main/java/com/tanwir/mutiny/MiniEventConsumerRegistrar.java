package com.tanwir.mutiny;

import com.tanwir.arc.ArcContainer;
import io.vertx.core.Vertx;

/**
 * SPI for generated EventBus consumer registrations.
 *
 * <p>Mirrors the pattern used by Quarkus's {@code VertxProcessor} which generates an
 * {@code EventConsumerInvoker} at build time for every {@link ConsumeEvent} method.
 *
 * <p>The ARC annotation processor generates exactly one implementation of this interface
 * (named {@code com.tanwir.mutiny.generated.GeneratedEventConsumerRegistrar}) and registers
 * it under {@code META-INF/services/com.tanwir.mutiny.MiniEventConsumerRegistrar}.
 *
 * <p>At startup, {@link MiniEventBus#initialize(Vertx, ArcContainer)} loads all registrars
 * via {@link java.util.ServiceLoader} and calls {@link #register(Vertx, ArcContainer)}.
 */
public interface MiniEventConsumerRegistrar {

    /**
     * Registers all {@link ConsumeEvent} handlers with the Vert.x EventBus.
     *
     * @param vertx        the running Vert.x instance
     * @param arcContainer the ARC container used to resolve bean instances
     */
    void register(Vertx vertx, ArcContainer arcContainer);
}
