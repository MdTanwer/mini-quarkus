package com.tanwir.arc.interceptor;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Default {@link InvocationContext} implementation used by generated {@code _Subclass} code.
 *
 * <p>Wraps a {@link Callable} that represents the "rest of the chain" — either the next
 * interceptor or the actual target bean method invocation (via {@code super.method(args)}).
 *
 * <h2>Usage in generated subclasses</h2>
 * The {@link com.tanwir.arc.processor.ArcBeanProcessor} generates subclass code like:
 * <pre>{@code
 * @Override
 * public Product create(Product product) {
 *     InvocationContext _ctx = new SimpleInvocationContext(
 *         this, "create", new Object[]{product},
 *         () -> (Object) super.create(product)   // the actual call
 *     );
 *     // wrap with interceptors
 *     _ctx = SimpleInvocationContext.chain(_ctx, new TimingInterceptor());
 *     _ctx = SimpleInvocationContext.chain(_ctx, new LoggingInterceptor());
 *     try {
 *         return (Product) _ctx.proceed();
 *     } catch (RuntimeException _e) { throw _e; }
 *       catch (Exception _e) { throw new RuntimeException(_e); }
 * }
 * }</pre>
 *
 * <h2>Chain construction</h2>
 * {@link #chain(InvocationContext, Object)} creates a new context that wraps the given
 * interceptor around the existing context. When {@code proceed()} is called on the outer
 * context, it invokes the interceptor's {@link AroundInvoke} method with the inner context
 * as its argument. The interceptor then calls {@code ctx.proceed()} which continues to the
 * inner context — and so on until the final target method is called.
 */
public final class SimpleInvocationContext implements InvocationContext {

    private final Object target;
    private final String methodName;
    private Object[] parameters;
    private final Callable<Object> proceed;

    /**
     * Creates a context for a target method invocation.
     *
     * @param target     the bean instance (typically {@code this} in the subclass)
     * @param methodName the simple name of the intercepted method
     * @param parameters the method arguments (may be empty but not null)
     * @param proceed    callable that performs the actual method call ({@code super.method(args)})
     */
    public SimpleInvocationContext(Object target, String methodName, Object[] parameters,
                                   Callable<Object> proceed) {
        this.target = target;
        this.methodName = methodName;
        this.parameters = parameters;
        this.proceed = proceed;
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public Object[] getParameters() {
        return parameters;
    }

    @Override
    public void setParameters(Object[] params) {
        this.parameters = params;
    }

    @Override
    public Object proceed() throws Exception {
        return proceed.call();
    }

    // -------------------------------------------------------------------------
    // Chain construction
    // -------------------------------------------------------------------------

    /**
     * Wraps an interceptor around the given {@code inner} context.
     *
     * <p>The returned context, when {@code proceed()} is called, will:
     * <ol>
     *   <li>Find the {@link AroundInvoke}-annotated method on the interceptor instance</li>
     *   <li>Invoke it with {@code inner} as the {@link InvocationContext} argument</li>
     * </ol>
     *
     * <p>This constructs the interceptor chain from innermost (target method) outward
     * to outermost (first interceptor in the list). The last call to {@code chain()}
     * produces the outermost context that callers interact with.
     *
     * @param inner       the existing context (inner chain or target method invocation)
     * @param interceptor the interceptor instance to wrap around
     * @return a new context representing the wrapped call
     */
    public static InvocationContext chain(InvocationContext inner, Object interceptor) {
        // Find the @AroundInvoke method on the interceptor
        Method aroundInvoke = findAroundInvoke(interceptor.getClass());
        return new SimpleInvocationContext(
                inner.getTarget(),
                inner.getMethodName(),
                inner.getParameters(),
                () -> {
                    try {
                        return aroundInvoke.invoke(interceptor, inner);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        Throwable cause = ite.getCause();
                        if (cause instanceof Exception e) throw e;
                        throw new RuntimeException(cause);
                    }
                });
    }

    private static Method findAroundInvoke(Class<?> interceptorClass) {
        for (Method m : interceptorClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(AroundInvoke.class)) {
                m.setAccessible(true);
                return m;
            }
        }
        // Check superclasses
        Class<?> parent = interceptorClass.getSuperclass();
        if (parent != null && parent != Object.class) {
            return findAroundInvoke(parent);
        }
        throw new IllegalStateException(
                "No @AroundInvoke method found on interceptor: " + interceptorClass.getName());
    }
}
