package com.tanwir.hibernate;

import com.tanwir.config.MiniConfig;

/**
 * Maps {@code quarkus.hibernate-orm.*} (and {@code mini.hibernate-orm.*}) to Hibernate settings.
 */
public final class MiniHibernateConfig {
    private MiniHibernateConfig() {}

    /** Mirrors {@code quarkus.hibernate-orm.database.generation}. */
    public static String hbm2ddl() {
        String v = first(
                System.getProperty("quarkus.hibernate-orm.database.generation"),
                System.getProperty("mini.hibernate-orm.database.generation"),
                MiniConfig.getInstance().getValue("quarkus.hibernate-orm.database.generation"),
                MiniConfig.getInstance().getValue("mini.hibernate-orm.database.generation"));
        return v != null && !v.isBlank() ? v.trim() : "none";
    }

    public static String physicalNamingStrategy() {
        return first(
                System.getProperty("quarkus.hibernate-orm.naming.physical-strategy"),
                MiniConfig.getInstance().getValue("quarkus.hibernate-orm.naming.physical-strategy"));
    }

    private static String first(String... v) {
        for (String x : v) {
            if (x != null && !x.isBlank()) {
                return x.trim();
            }
        }
        return null;
    }
}
