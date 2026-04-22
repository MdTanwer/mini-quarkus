package com.tanwir.arc.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates the interceptor method that wraps a target bean method invocation.
 *
 * <p>Mirrors {@code jakarta.interceptor.AroundInvoke}. The annotated method must have
 * the signature:
 * <pre>{@code
 * @AroundInvoke
 * public Object methodName(InvocationContext ctx) throws Exception { ... }
 * }</pre>
 *
 * <p>The method must call {@link InvocationContext#proceed()} to continue the interceptor
 * chain and eventually invoke the target bean method. Failing to call {@code proceed()}
 * prevents the method from being invoked — this is intentional (e.g., caching interceptors).
 *
 * <p>The return value of the {@code @AroundInvoke} method becomes the return value
 * seen by the caller. The method may return {@code ctx.proceed()} directly (passthrough),
 * transform the result, or return a different value altogether.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AroundInvoke {
}
