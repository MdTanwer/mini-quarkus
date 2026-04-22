package com.tanwir.hibernate.deployment;

import com.tanwir.core.deployment.MultiBuildItem;

/** One JPA {@code jakarta.persistence.Entity} discovered at build time. */
public final class JpaEntityBuildItem extends MultiBuildItem {

    private final Class<?> entityClass;

    public JpaEntityBuildItem(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    public Class<?> entityClass() {
        return entityClass;
    }
}
