package com.tanwir.arc.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interceptor binding that activates {@link LoggingInterceptor}.
 *
 * <p>Apply to a CDI bean method (or class) to have every invocation logged:
 * <pre>{@code
 * @Singleton
 * public class ProductService {
 *     @Logged
 *     public List<Product> listAll() { ... }
 *
 *     @Logged              // class-level would apply to all methods
 *     public Product save(Product p) { ... }
 * }
 * }</pre>
 *
 * <h2>Real Quarkus comparison</h2>
 * In real Quarkus you would define a custom interceptor binding like this:
 * <pre>{@code
 * @InterceptorBinding
 * @Retention(RUNTIME) @Target({METHOD, TYPE})
 * public @interface Logged {}
 *
 * @Logged @Interceptor @Priority(Interceptor.Priority.APPLICATION)
 * public class LoggingInterceptor {
 *     @AroundInvoke
 *     public Object log(InvocationContext ctx) throws Exception { ... }
 * }
 * }</pre>
 * This is exactly what mini-quarkus provides here — just built-in.
 */
@InterceptorBinding
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Logged {
}
