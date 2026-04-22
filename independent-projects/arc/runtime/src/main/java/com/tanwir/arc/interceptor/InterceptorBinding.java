package com.tanwir.arc.interceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that designates an annotation as a CDI interceptor binding.
 *
 * <p>Mirrors {@code jakarta.interceptor.InterceptorBinding}. Any annotation annotated
 * with {@code @InterceptorBinding} becomes a "binding" that ties a CDI bean method to
 * an interceptor class. The {@link com.tanwir.arc.processor.ArcBeanProcessor} discovers
 * these bindings at compile time and generates a {@code _Subclass} that weaves the
 * interceptor chain.
 *
 * <h2>Defining a custom interceptor</h2>
 * <ol>
 *   <li>Declare a binding annotation:<pre>{@code
 * @InterceptorBinding
 * @Retention(RUNTIME)
 * @Target({METHOD, TYPE})
 * public @interface MyBinding {}
 * }</pre>
 *   </li>
 *   <li>Implement an interceptor class:<pre>{@code
 * @Interceptor
 * @MyBinding
 * public class MyInterceptor {
 *     @AroundInvoke
 *     public Object intercept(InvocationContext ctx) throws Exception {
 *         System.out.println("before");
 *         Object result = ctx.proceed();
 *         System.out.println("after");
 *         return result;
 *     }
 * }
 * }</pre>
 *   </li>
 *   <li>Apply the binding to a CDI bean method:<pre>{@code
 * @Singleton
 * public class MyService {
 *     @MyBinding
 *     public String doWork() { ... }
 * }
 * }</pre>
 *   </li>
 * </ol>
 *
 * <h2>Built-in bindings</h2>
 * <ul>
 *   <li>{@link Logged} — logs method entry/exit via {@link LoggingInterceptor}</li>
 *   <li>{@link Timed} — records method duration via {@link TimingInterceptor}</li>
 * </ul>
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface InterceptorBinding {
}
