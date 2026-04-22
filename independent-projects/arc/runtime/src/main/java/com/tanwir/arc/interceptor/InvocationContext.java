package com.tanwir.arc.interceptor;

/**
 * Context object passed to {@link AroundInvoke} interceptor methods.
 *
 * <p>Mirrors {@code jakarta.interceptor.InvocationContext}. Provides:
 * <ul>
 *   <li>The target object ({@link #getTarget()})</li>
 *   <li>The method name ({@link #getMethodName()})</li>
 *   <li>The method arguments ({@link #getParameters()}/{@link #setParameters(Object[])})</li>
 *   <li>The ability to continue the chain ({@link #proceed()})</li>
 * </ul>
 *
 * <h2>Interceptor chain</h2>
 * When multiple interceptors apply to a method (e.g., both {@link Logged} and {@link Timed}),
 * the processor generates a chain of {@link SimpleInvocationContext} wrappers. Each wrapper
 * holds a reference to the next context in the chain (or the final bean invocation).
 * Calling {@code proceed()} delegates to the next link. This mirrors the real CDI
 * interceptor invocation chain implemented in Quarkus's
 * {@code io.quarkus.arc.impl.InterceptedMethodMetadata}.
 *
 * <pre>
 *   caller → LoggingInterceptor.aroundInvoke(ctx1)
 *               → ctx1.proceed()
 *                  → TimingInterceptor.aroundInvoke(ctx2)
 *                      → ctx2.proceed()
 *                          → ProductResource.create(product)   ← the actual method
 *                      ← return result
 *                  ← return result
 *               ← return result
 *          ← return result
 * </pre>
 */
public interface InvocationContext {

    /** Returns the target bean instance being intercepted. */
    Object getTarget();

    /** Returns the name of the intercepted method. */
    String getMethodName();

    /** Returns the current argument array for the intercepted method. */
    Object[] getParameters();

    /**
     * Replaces the argument array. Interceptors may modify arguments before
     * calling {@code proceed()} (e.g., input validation or decoration).
     */
    void setParameters(Object[] params);

    /**
     * Proceeds to the next interceptor in the chain, or to the target method
     * if this is the last interceptor.
     *
     * @return the return value from the next interceptor or the target method
     * @throws Exception if the target method throws
     */
    Object proceed() throws Exception;
}
