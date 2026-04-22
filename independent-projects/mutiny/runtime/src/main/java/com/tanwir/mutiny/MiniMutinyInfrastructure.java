package com.tanwir.mutiny;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;
import org.jboss.logging.Logger;

/**
 * Configures SmallRye Mutiny to use the Vert.x event-loop executor.
 *
 * <p>In real Quarkus this is done by {@code io.quarkus.mutiny.runtime.MutinyInfrastructure}
 * inside the {@code quarkus-mutiny} extension. The recorder sets the default executor to
 * Vert.x's event-loop group so that {@code Uni.subscribe().with(...)} callbacks run on the
 * same thread as the Vert.x handler — avoiding thread switches for simple reactive pipelines.
 *
 * <p>Call {@link #initialize(Vertx)} once, before the HTTP server starts.
 */
public final class MiniMutinyInfrastructure {

    private static final Logger LOG = Logger.getLogger(MiniMutinyInfrastructure.class);

    private MiniMutinyInfrastructure() {
    }

    /**
     * Wires Mutiny's default executor to the Vert.x event-loop thread group.
     *
     * <p>After this call, {@code Uni} subscribers that do not specify a custom executor
     * will run on a Vert.x event-loop thread — the same model as the real Quarkus runtime.
     */
    public static void initialize(Vertx vertx) {
        Infrastructure.setDefaultExecutor(command -> vertx.nettyEventLoopGroup().execute(command));
        LOG.info("Mutiny infrastructure wired to Vert.x event-loop executor");
    }

    /**
     * Resets Mutiny's executor back to the fork-join pool (useful for tests that manage their own Vert.x).
     */
    public static void reset() {
        Infrastructure.setDefaultExecutor(java.util.concurrent.ForkJoinPool.commonPool());
    }
}
