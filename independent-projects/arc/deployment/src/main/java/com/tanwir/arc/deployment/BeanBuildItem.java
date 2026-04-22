package com.tanwir.arc.deployment;

import com.tanwir.core.deployment.MultiBuildItem;

/**
 * Represents a CDI bean class discovered during the deployment phase.
 *
 * <p>Mirrors {@code io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem} and
 * {@code io.quarkus.arc.deployment.AdditionalBeanBuildItem} from real Quarkus.
 * Multiple instances of {@code BeanBuildItem} can be produced — one per discovered bean.
 *
 * <p>The {@link ArcDeploymentProcessor} produces these for every class annotated with
 * a CDI scope annotation ({@code @Singleton}, {@code @ApplicationScoped}, etc.).
 * The {@link ArcRecorder} consumes them to register the beans with the ARC container.
 */
public final class BeanBuildItem extends MultiBuildItem {

    private final String beanClassName;
    private final String scope;

    public BeanBuildItem(String beanClassName, String scope) {
        this.beanClassName = beanClassName;
        this.scope = scope;
    }

    /** Fully-qualified class name of the bean. */
    public String beanClassName() {
        return beanClassName;
    }

    /** Scope annotation simple name (e.g. {@code "Singleton"}, {@code "ApplicationScoped"}). */
    public String scope() {
        return scope;
    }

    @Override
    public String toString() {
        return "BeanBuildItem[" + scope + " " + beanClassName + "]";
    }
}
