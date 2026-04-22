package com.tanwir.panache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method (or all methods of a class) as requiring a database transaction.
 *
 * <p>Mirrors {@code jakarta.transaction.Transactional}. In the real Quarkus framework this
 * is a CDI interceptor binding backed by the Narayana JTA transaction manager. In
 * mini-quarkus it triggers two behaviours:
 *
 * <ol>
 *   <li><b>REST routes</b>: The {@code MiniResteasyReactiveProcessor} detects
 *       {@code @Transactional} on resource methods and generates route handlers that
 *       wrap the method call with {@link TransactionManager#begin}/{@link TransactionManager#commit}/
 *       {@link TransactionManager#rollback} — the same pattern used by Quarkus's
 *       {@code io.quarkus.narayana.jta.runtime.TransactionRecorder}.</li>
 *   <li><b>Repositories</b>: {@link MiniRepositoryBase} participates in the current
 *       thread-local transaction if one is active, otherwise each operation auto-commits.</li>
 * </ol>
 *
 * <h2>Real Quarkus comparison</h2>
 * <pre>
 *   mini-quarkus @Transactional  →  interceptor in generated route handler (compile time)
 *   real Quarkus @Transactional  →  CDI @InterceptorBinding → TransactionInterceptor (AOP)
 * </pre>
 * Full CDI interceptor support (Phase 8) will make {@code @Transactional} work on any
 * CDI bean method, not just REST resource methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Transactional {
}
