package com.tanwir.arc.interceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Built-in interceptor that measures and records method execution time.
 *
 * <p>Activated by the {@link Timed} interceptor binding. Records:
 * <ul>
 *   <li>Total invocations</li>
 *   <li>Last execution time (ms)</li>
 *   <li>Total accumulated time (ms)</li>
 * </ul>
 *
 * <h2>Log output</h2>
 * <pre>
 * INFO  [arc.interceptor.TimingInterceptor] ⏱ ProductResource.create() took 4 ms
 *   (calls=1, total=4 ms, avg=4 ms)
 * </pre>
 *
 * <h2>Metrics access</h2>
 * Use {@link #getStats(String)} to retrieve accumulated statistics for a specific method.
 *
 * <h2>Real Quarkus comparison</h2>
 * In real Quarkus, {@code @Timed} from SmallRye Metrics / Micrometer fires via a CDI
 * interceptor that delegates to {@code io.micrometer.core.instrument.Timer.record()}.
 * This is the exact same pattern but with an in-memory store instead of a Micrometer registry.
 * Replacing {@code stats} with a {@code MeterRegistry.timer(methodKey).record(...)} would
 * give you the real Quarkus implementation.
 */
@Interceptor
@Timed
public class TimingInterceptor {

    private static final Logger LOG = Logger.getLogger(TimingInterceptor.class.getName());

    /** Per-method statistics: method-key → [invocationCount, totalMs]. */
    private static final ConcurrentHashMap<String, long[]> STATS = new ConcurrentHashMap<>();

    /**
     * Times the target method invocation and records statistics.
     */
    @AroundInvoke
    public Object time(InvocationContext ctx) throws Exception {
        String target = ctx.getTarget().getClass().getSimpleName();
        if (target.endsWith("_Subclass")) {
            target = target.substring(0, target.length() - "_Subclass".length());
        }
        String key = target + "." + ctx.getMethodName() + "()";

        long start = System.nanoTime();
        Object result;
        try {
            result = ctx.proceed();
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            LOG.warning("⏱ " + key + " failed after " + ms + " ms");
            throw e;
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        long[] stats = STATS.computeIfAbsent(key, k -> new long[]{0, 0});
        stats[0]++;
        stats[1] += ms;

        LOG.info("⏱ " + key + " took " + ms + " ms"
                + "  (calls=" + stats[0] + ", total=" + stats[1] + " ms, avg=" + (stats[1] / stats[0]) + " ms)");
        return result;
    }

    /**
     * Returns statistics for the given method key (e.g. {@code "ProductResource.create()"}).
     *
     * @return array of {@code [invocationCount, totalMs]}, or {@code null} if never called
     */
    public static long[] getStats(String methodKey) {
        return STATS.get(methodKey);
    }
}
