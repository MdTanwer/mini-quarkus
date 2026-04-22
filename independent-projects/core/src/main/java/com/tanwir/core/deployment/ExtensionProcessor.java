package com.tanwir.core.deployment;

/**
 * Marker interface for classes that contain {@link BuildStep} methods.
 *
 * <p>Mirrors the role of Quarkus extension deployment processors (e.g.
 * {@code io.quarkus.arc.deployment.ArcProcessor}). All classes implementing this
 * interface are discovered at deployment time via {@link java.util.ServiceLoader}.
 *
 * <p>A deployment processor class:
 * <ul>
 *   <li>Must have a no-argument constructor (instantiated by the manager)</li>
 *   <li>Contains one or more {@link BuildStep}-annotated methods</li>
 *   <li>Is registered in {@code META-INF/services/com.tanwir.core.deployment.ExtensionProcessor}</li>
 * </ul>
 *
 * <pre>{@code
 * // META-INF/services/com.tanwir.core.deployment.ExtensionProcessor:
 * com.tanwir.arc.deployment.ArcDeploymentProcessor
 * com.tanwir.resteasy.reactive.deployment.ResteasyReactiveDeploymentProcessor
 * com.tanwir.mutiny.deployment.MutinyDeploymentProcessor
 * }</pre>
 */
public interface ExtensionProcessor {
}
