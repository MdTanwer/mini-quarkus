package com.tanwir.core.deployment;

/**
 * Root type for all build-time data carriers.
 *
 * <p>Mirrors {@code io.quarkus.builder.item.BuildItem} from real Quarkus. A {@link BuildItem}
 * is an immutable value object produced by one {@link BuildStep} and consumed by another.
 * This is the fundamental unit of communication between extensions during the deployment phase.
 *
 * <p>Concrete build items must extend either:
 * <ul>
 *   <li>{@link SimpleBuildItem} — only one instance of this type may be produced</li>
 *   <li>{@link MultiBuildItem}  — multiple instances of this type may be produced</li>
 * </ul>
 *
 * <p>Example hierarchy from the real Quarkus codebase:
 * <pre>{@code
 * // Exactly one BeanContainerBuildItem exists per build
 * public final class BeanContainerBuildItem extends SimpleBuildItem { ... }
 *
 * // Many BeanDefiningAnnotationBuildItems can be produced by different extensions
 * public final class BeanDefiningAnnotationBuildItem extends MultiBuildItem { ... }
 * }</pre>
 */
public abstract class BuildItem {

    // Marker supertype — no state. BuildItem identity is purely by type.
    protected BuildItem() {
    }
}
