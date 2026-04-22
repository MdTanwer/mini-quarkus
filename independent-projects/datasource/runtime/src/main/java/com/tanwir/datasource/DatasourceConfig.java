package com.tanwir.datasource;

import com.tanwir.config.MiniConfig;

/**
 * Resolves Quarkus-style {@code quarkus.datasource.*} with {@code mini.datasource.*} fallbacks
 * and {@link System#getProperty} overrides — mirrors the real config key story.
 */
public final class DatasourceConfig {
    private DatasourceConfig() {}

    public static String jdbcUrl() {
        return firstNonNullOrEmpty(
                System.getProperty("quarkus.datasource.jdbc.url"),
                System.getProperty("mini.datasource.url"),
                MiniConfig.getInstance().getValue("quarkus.datasource.jdbc.url"),
                MiniConfig.getInstance().getValue("mini.datasource.url"))
                .orElse("jdbc:h2:mem:minidb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    }

    public static String username() {
        return firstNonNullOrEmpty(
                System.getProperty("quarkus.datasource.username"),
                System.getProperty("mini.datasource.username"),
                MiniConfig.getInstance().getValue("quarkus.datasource.username"),
                MiniConfig.getInstance().getValue("mini.datasource.username"))
                .orElse("sa");
    }

    public static String password() {
        if (System.getProperty("quarkus.datasource.password") != null) {
            return System.getProperty("quarkus.datasource.password");
        }
        if (System.getProperty("mini.datasource.password") != null) {
            return System.getProperty("mini.datasource.password");
        }
        String p = MiniConfig.getInstance().getValue("quarkus.datasource.password");
        if (p != null) {
            return p;
        }
        p = MiniConfig.getInstance().getValue("mini.datasource.password");
        return p != null ? p : "";
    }

    private static java.util.Optional<String> firstNonNullOrEmpty(String... candidates) {
        for (String x : candidates) {
            if (x != null && !x.isBlank()) {
                return java.util.Optional.of(x.trim());
            }
        }
        return java.util.Optional.empty();
    }
}
