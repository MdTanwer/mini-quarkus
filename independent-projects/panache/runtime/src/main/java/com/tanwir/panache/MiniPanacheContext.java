package com.tanwir.panache;

import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central runtime state for the Panache extension.
 *
 * <p>Holds the configured {@link DataSource}, the list of registered entity classes,
 * and provides schema generation. Initialized by the {@code PanacheRecorder} during
 * the deployment phase.
 *
 * <p>Mirrors the role of {@code io.quarkus.hibernate.orm.runtime.HibernateOrmRuntimeConfig}
 * + {@code io.quarkus.agroal.runtime.DataSourcesJdbcRuntimeConfig} in real Quarkus —
 * a single place that holds the runtime-configured persistence infrastructure.
 */
public final class MiniPanacheContext {

    private static final Logger LOG = Logger.getLogger(MiniPanacheContext.class);

    private static volatile DataSource dataSource;
    private static final List<Class<?>> entityClasses = new ArrayList<>();

    private MiniPanacheContext() {}

    // -------------------------------------------------------------------------
    // Initialization — called by PanacheRecorder at deployment time
    // -------------------------------------------------------------------------

    /** Registers the data source. Called once at startup by the deployment recorder. */
    public static void setDataSource(DataSource ds) {
        dataSource = ds;
        LOG.info("[panache] DataSource configured");
    }

    /** Returns the configured data source. */
    public static DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Registers an entity class for schema generation.
     * Called by the deployment recorder for each discovered {@link MiniEntity}.
     */
    public static void registerEntity(Class<?> entityClass) {
        entityClasses.add(entityClass);
    }

    /**
     * Creates all registered entity tables (if they don't already exist).
     * Mirrors Hibernate's {@code hibernate.hbm2ddl.auto=update} behaviour.
     */
    public static void createSchema() {
        LOG.infof("[panache] Creating schema for %d entity class(es)", entityClasses.size());
        for (Class<?> entity : entityClasses) {
            try {
                createTable(entity);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create table for " + entity.getName(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Schema generation — mirrors Hibernate's SchemaExport
    // -------------------------------------------------------------------------

    static void createTable(Class<?> entityClass) throws SQLException {
        MiniEntity annotation = entityClass.getAnnotation(MiniEntity.class);
        if (annotation == null) {
            throw new IllegalArgumentException(entityClass.getName() + " is not annotated with @MiniEntity");
        }
        String tableName = resolveTableName(entityClass, annotation);
        Map<String, String> columns = resolveColumns(entityClass); // columnName → SQL type

        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
        List<String> colDefs = new ArrayList<>();
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            colDefs.add("    " + entry.getKey() + " " + entry.getValue());
        }
        ddl.append(String.join(",\n", colDefs)).append("\n)");

        LOG.infof("[panache] DDL: %s", ddl);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helpers — used by both schema generation and MiniRepositoryBase
    // -------------------------------------------------------------------------

    /** Resolves the SQL table name for an entity class. */
    public static String resolveTableName(Class<?> entityClass, MiniEntity annotation) {
        String name = annotation.tableName();
        return name.isEmpty() ? entityClass.getSimpleName().toLowerCase() : name;
    }

    /**
     * Returns an ordered map of columnName → SQL column definition for all fields.
     * The {@link Id}-annotated field gets {@code BIGINT AUTO_INCREMENT PRIMARY KEY}.
     */
    public static Map<String, String> resolveColumns(Class<?> entityClass) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (ColumnInfo info : columnInfos(entityClass)) {
            String sqlType = javaToSqlType(info.javaType(), info.isId());
            columns.put(info.columnName(), sqlType);
        }
        return columns;
    }

    /** Returns ordered metadata about all mapped fields/record components. */
    public static List<ColumnInfo> columnInfos(Class<?> entityClass) {
        List<ColumnInfo> infos = new ArrayList<>();
        if (entityClass.isRecord()) {
            for (RecordComponent rc : entityClass.getRecordComponents()) {
                boolean isId = rc.isAnnotationPresent(Id.class);
                Column col = rc.getAnnotation(Column.class);
                String colName = (col != null && !col.value().isEmpty())
                        ? col.value() : toSnakeCase(rc.getName());
                infos.add(new ColumnInfo(rc.getName(), colName, rc.getType(), isId));
            }
        } else {
            for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                boolean isId = field.isAnnotationPresent(Id.class);
                Column col = field.getAnnotation(Column.class);
                String colName = (col != null && !col.value().isEmpty())
                        ? col.value() : toSnakeCase(field.getName());
                infos.add(new ColumnInfo(field.getName(), colName, field.getType(), isId));
            }
        }
        return infos;
    }

    /** Maps Java types to H2 SQL types. */
    static String javaToSqlType(Class<?> javaType, boolean isPrimaryKey) {
        if (isPrimaryKey) return "BIGINT AUTO_INCREMENT PRIMARY KEY";
        if (javaType == String.class)            return "VARCHAR(255)";
        if (javaType == Long.class || javaType == long.class) return "BIGINT";
        if (javaType == Integer.class || javaType == int.class) return "INTEGER";
        if (javaType == Boolean.class || javaType == boolean.class) return "BOOLEAN";
        if (javaType == BigDecimal.class)        return "DECIMAL(19,2)";
        if (javaType == LocalDate.class)         return "DATE";
        if (javaType == LocalDateTime.class)     return "TIMESTAMP";
        if (javaType == Double.class || javaType == double.class) return "DOUBLE";
        if (javaType == Float.class || javaType == float.class)   return "FLOAT";
        return "VARCHAR(255)"; // fallback
    }

    /** Converts camelCase to snake_case: {@code unitPrice} → {@code unit_price}. */
    public static String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** Metadata about a single mapped field. */
    public record ColumnInfo(String fieldName, String columnName, Class<?> javaType, boolean isId) {}
}
