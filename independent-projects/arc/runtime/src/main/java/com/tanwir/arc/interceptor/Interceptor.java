package com.tanwir.arc.interceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a CDI interceptor.
 *
 * <p>Mirrors {@code jakarta.interceptor.Interceptor}. An interceptor class must:
 * <ol>
 *   <li>Be annotated with {@code @Interceptor}</li>
 *   <li>Carry one or more {@link InterceptorBinding} annotations (e.g., {@link Logged})</li>
 *   <li>Have exactly one {@link AroundInvoke}-annotated method</li>
 * </ol>
 *
 * <p>The {@link com.tanwir.arc.processor.ArcBeanProcessor} discovers interceptor classes
 * at compile time. Built-in interceptors (defined in this module) are registered via
 * {@code META-INF/mini-interceptors.txt}; user-defined interceptors are detected as
 * {@link ElementType#TYPE}-annotated root elements in the compilation unit.
 *
 * <pre>{@code
 * @Interceptor
 * @Logged                     // the binding this interceptor handles
 * public class LoggingInterceptor {
 *     @AroundInvoke
 *     public Object log(InvocationContext ctx) throws Exception {
 *         LOG.infof("→ %s", ctx.getMethodName());
 *         Object result = ctx.proceed();
 *         LOG.infof("← %s", ctx.getMethodName());
 *         return result;
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Interceptor {
}
