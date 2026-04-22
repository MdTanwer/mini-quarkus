package com.tanwir.core.deployment;

/**
 * When a recorded method is executed at runtime.
 *
 * <p>Mirrors {@code io.quarkus.deployment.annotations.ExecutionTime}.
 * In real Quarkus this controls whether bytecode is replayed during:
 * <ul>
 *   <li>{@link #STATIC_INIT} — very early in startup, in the JVM static initializer phase</li>
 *   <li>{@link #RUNTIME_INIT} — after configuration is loaded but before HTTP server starts</li>
 * </ul>
 *
 * <p>In mini-quarkus both phases run sequentially at startup, before the HTTP server begins
 * accepting connections. The distinction is preserved for conceptual accuracy.
 */
public enum ExecutionTime {

    /**
     * Executed during static initialization — earliest possible phase.
     * In native image this runs before the image is "snapshotted".
     * Use for framework infrastructure that must be ready before anything else.
     */
    STATIC_INIT,

    /**
     * Executed after static init, closer to when the application becomes ready.
     * Use for setup that needs runtime configuration (ports, data sources, etc.).
     */
    RUNTIME_INIT
}
