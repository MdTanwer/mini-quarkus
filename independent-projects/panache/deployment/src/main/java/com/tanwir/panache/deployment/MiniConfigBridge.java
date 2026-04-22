package com.tanwir.panache.deployment;

import com.tanwir.config.MiniConfig;

/**
 * Resolves application properties in deployment recorders.
 */
public final class MiniConfigBridge {
    private MiniConfigBridge() {}

    public static boolean panacheDdlNone() {
        return "none".equalsIgnoreCase(
                MiniConfig.getInstance().getValue("mini.panache.ddl.auto", "update").trim());
    }
}
