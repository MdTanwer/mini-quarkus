package com.tanwir.flyway;

import com.tanwir.config.MiniConfig;

/** Quarkus-style keys: {@code quarkus.flyway.*} and {@code mini.flyway.*} fallbacks. */
public final class FlywayConfig {
    private FlywayConfig() {}

    public static boolean migrateAtStart() {
        String s = first(
                System.getProperty("quarkus.flyway.migrate-at-start"),
                System.getProperty("mini.flyway.migrate-at-start"),
                MiniConfig.getInstance().getValue("quarkus.flyway.migrate-at-start"),
                MiniConfig.getInstance().getValue("mini.flyway.migrate-at-start"));
        if (s == null) {
            return false;
        }
        return Boolean.parseBoolean(s.trim());
    }

    public static String[] locations() {
        String s = first(
                System.getProperty("quarkus.flyway.locations"),
                System.getProperty("mini.flyway.locations"),
                MiniConfig.getInstance().getValue("quarkus.flyway.locations"),
                MiniConfig.getInstance().getValue("mini.flyway.locations"));
        if (s == null || s.isBlank()) {
            return new String[] { "classpath:db/migration" };
        }
        return s.split(",\\s*");
    }

    public static String baselineVersion() {
        return first(
                System.getProperty("quarkus.flyway.baseline-version"),
                MiniConfig.getInstance().getValue("quarkus.flyway.baseline-version"),
                MiniConfig.getInstance().getValue("mini.flyway.baseline-version"));
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
