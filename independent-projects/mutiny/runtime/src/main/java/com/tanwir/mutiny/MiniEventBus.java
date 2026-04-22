package com.tanwir.mutiny;

import com.tanwir.arc.ArcContainer;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.jboss.logging.Logger;

import java.util.ServiceLoader;

/**
 * Injectable Vert.x EventBus wrapper with Mutiny reactive API.
 *
 * <p>Mirrors what Quarkus provides as {@code io.vertx.mutiny.core.eventbus.EventBus}
 * (the Mutiny bindings generated for Vert.x). In real Quarkus this bean is produced by
 * {@code io.quarkus.vertx.core.runtime.VertxCoreRecorder} and is injectable anywhere.
 *
 * <p>Three interaction patterns:
 * <ol>
 *   <li><b>publish</b> — broadcast to all consumers (fire-and-forget)</li>
 *   <li><b>send</b>    — point-to-point to one consumer (fire-and-forget)</li>
 *   <li><b>request</b> — point-to-point with a {@link Uni}-wrapped reply</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Singleton
 * public class OrderService {
 *
 *     @Inject
 *     MiniEventBus eventBus;
 *
 *     public Uni<String> placeOrder(String item) {
 *         return eventBus.request("order.placed", item)
 *                 .map(reply -> "Order confirmed: " + reply.body());
 *     }
 * }
 * }</pre>
 */
public final class MiniEventBus {

    private static final Logger LOG = Logger.getLogger(MiniEventBus.class);

    private final EventBus delegate;

    private MiniEventBus(EventBus delegate) {
        this.delegate = delegate;
    }

    /**
     * Initialises the EventBus, wires Mutiny to Vert.x, discovers and registers all
     * {@link ConsumeEvent} handlers found via {@link ServiceLoader}.
     *
     * <p>Called once by {@code MiniResteasyReactiveServer} before the HTTP server starts.
     */
    public static MiniEventBus initialize(Vertx vertx, ArcContainer arcContainer) {
        MiniMutinyInfrastructure.initialize(vertx);

        MiniEventBus bus = new MiniEventBus(vertx.eventBus());

        int registrarCount = 0;
        for (MiniEventConsumerRegistrar registrar : ServiceLoader.load(MiniEventConsumerRegistrar.class)) {
            registrar.register(vertx, arcContainer);
            registrarCount++;
        }
        LOG.infof("EventBus initialized — %d consumer registrar(s) loaded", registrarCount);
        return bus;
    }

    // -------------------------------------------------------------------------
    // Publish (broadcast to all consumers on the address)
    // -------------------------------------------------------------------------

    /**
     * Broadcasts {@code message} to <em>all</em> consumers registered on {@code address}.
     * This is a fire-and-forget operation — no reply is expected.
     */
    public MiniEventBus publish(String address, Object message) {
        delegate.publish(address, message);
        return this;
    }

    /**
     * Broadcasts with custom {@link DeliveryOptions} (headers, timeout, codec name, etc.).
     */
    public MiniEventBus publish(String address, Object message, DeliveryOptions options) {
        delegate.publish(address, message, options);
        return this;
    }

    // -------------------------------------------------------------------------
    // Send (point-to-point, one consumer, no reply expected)
    // -------------------------------------------------------------------------

    /**
     * Sends {@code message} to <em>one</em> consumer on {@code address} (round-robin
     * if multiple consumers exist). Fire-and-forget — no reply is collected.
     */
    public MiniEventBus send(String address, Object message) {
        delegate.send(address, message);
        return this;
    }

    // -------------------------------------------------------------------------
    // Request (point-to-point with Uni<Message<T>> reply)
    // -------------------------------------------------------------------------

    /**
     * Sends a {@code body} to one consumer on {@code address} and returns a
     * {@link Uni} that resolves with the consumer's reply.
     *
     * <p>This is the reactive equivalent of {@code EventBus.request(address, body, handler)}.
     * The caller awaits the reply non-blockingly:
     *
     * <pre>{@code
     * eventBus.<String>request("greet", "World")
     *         .map(Message::body)
     *         .subscribe().with(reply -> System.out.println(reply));
     * }</pre>
     *
     * @param <T>     expected reply body type
     * @param address EventBus address
     * @param body    message payload
     * @return {@link Uni} resolving to the reply {@link Message}
     */
    public <T> Uni<Message<T>> request(String address, Object body) {
        return Uni.createFrom().completionStage(
                delegate.<T>request(address, body).toCompletionStage());
    }

    /**
     * Like {@link #request(String, Object)} but with custom {@link DeliveryOptions}.
     */
    public <T> Uni<Message<T>> request(String address, Object body, DeliveryOptions options) {
        return Uni.createFrom().completionStage(
                delegate.<T>request(address, body, options).toCompletionStage());
    }

    /**
     * Returns the underlying Vert.x {@link EventBus} for advanced usage.
     */
    public EventBus delegate() {
        return delegate;
    }
}
