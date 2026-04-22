package com.tanwir.arc.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interceptor binding that activates {@link TimingInterceptor}.
 *
 * <p>Apply to a CDI bean method to record its execution time:
 * <pre>{@code
 * @Singleton
 * public class OrderService {
 *     @Timed
 *     public List<Order> processAll() { ... }
 * }
 * }</pre>
 *
 * <p>The measured time (in milliseconds) is printed to the log after every invocation.
 * A production version would integrate with Micrometer or OpenTelemetry — the same
 * way real Quarkus's {@code @Timed} from SmallRye Metrics works under the hood via
 * an interceptor binding registered with CDI.
 *
 * <h2>Real Quarkus comparison</h2>
 * Quarkus SmallRye Metrics registers {@code @Timed} as an interceptor binding.
 * Its implementation delegates to Micrometer's {@code Timer.record()} in its
 * {@code @AroundInvoke} method — the exact pattern {@link TimingInterceptor} follows here.
 */
@InterceptorBinding
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Timed {
}
