package com.tanwir.core.deployment;

import javax.sql.DataSource;

/**
 * Signals that the default JDBC data source is ready. Mirrors
 * the role of the Agroal-initialized / JDBC-capable datasource in real Quarkus.
 */
public final class DataSourceBuildItem extends SimpleBuildItem {

    private final DataSource dataSource;

    public DataSourceBuildItem(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }
}
