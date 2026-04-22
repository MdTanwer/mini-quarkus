package com.tanwir.core.deployment;

/**
 * Produced after Flyway has run (or been skipped) so that Hibernate ORM and
 * optional DDL steps can run in a well-defined order — mirroring
 * {@code quarkus.flyway} completing before {@code quarkus-hibernate-orm} in real Quarkus.
 */
public final class MigrationsCompleteBuildItem extends SimpleBuildItem {
}
