package com.tanwir.panache.deployment;

import com.tanwir.core.deployment.MultiBuildItem;

/**
 * Carries metadata about a single {@link com.tanwir.panache.MiniEntity}-annotated class.
 *
 * <p>Produced by {@link PanacheDeploymentProcessor#discoverEntities} once per entity
 * class found on the classpath. Consumed by {@link PanacheDeploymentProcessor#buildSchema}
 * to create the database table.
 *
 * <p>Mirrors {@code io.quarkus.hibernate.orm.deployment.HibernateOrmAnnotations.Entity}
 * from real Quarkus — where each discovered {@code @Entity} class is represented as a
 * build item so other extensions (like Panache, Envers, etc.) can consume it.
 */
public final class EntityBuildItem extends MultiBuildItem {

    private final Class<?> entityClass;

    public EntityBuildItem(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    /** The {@link com.tanwir.panache.MiniEntity}-annotated class. */
    public Class<?> entityClass() {
        return entityClass;
    }

    @Override
    public String toString() {
        return "EntityBuildItem[" + entityClass.getSimpleName() + "]";
    }
}
