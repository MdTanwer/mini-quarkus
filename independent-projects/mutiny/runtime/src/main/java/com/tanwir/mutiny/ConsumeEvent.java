package com.tanwir.mutiny;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CDI bean method as a Vert.x EventBus consumer.
 *
 * <p>Mirrors {@code io.quarkus.vertx.ConsumeEvent} from the real Quarkus vertx extension.
 * The annotated method is registered with the Vert.x EventBus at application startup by the
 * generated {@link MiniEventConsumerRegistrar}.
 *
 * <pre>{@code
 * @Singleton
 * public class GreetingConsumer {
 *
 *     @ConsumeEvent("greetings")
 *     public String onGreeting(String message) {
 *         return "Hello, " + message + "!";
 *     }
 *
 *     @ConsumeEvent(value = "heavy-work", blocking = true)
 *     public void doHeavyWork(String payload) {
 *         // runs on worker thread, not the event loop
 *     }
 * }
 * }</pre>
 *
 * <p>The method may return:
 * <ul>
 *   <li>{@code void} — fire-and-forget consumer</li>
 *   <li>Any object — reply value sent back to the caller</li>
 *   <li>{@code Uni<T>} — async reply (the Uni result is sent back when it resolves)</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConsumeEvent {

    /**
     * The EventBus address this method listens on.
     * Defaults to the fully-qualified class name of the declaring bean.
     */
    String value() default "";

    /**
     * If {@code true} the method is invoked on a Vert.x worker thread instead of the event loop.
     * Use this when the method performs blocking I/O.
     * Mirrors the {@code blocking} attribute of the real Quarkus {@code @ConsumeEvent}.
     */
    boolean blocking() default false;
}
