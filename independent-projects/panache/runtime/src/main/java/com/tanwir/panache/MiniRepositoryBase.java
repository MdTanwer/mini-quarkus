package com.tanwir.panache;

import org.jboss.logging.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed base class for {@link MiniRepository} implementations.
 *
 * <p>Mirrors the default method implementations in
 * {@code io.quarkus.hibernate.orm.panache.PanacheRepository}. Subclasses call
 * {@code super(EntityClass.class)} and inherit fully working CRUD operations backed by H2.
 *
 * <h2>How it maps entities to SQL</h2>
 * Uses reflection ({@link MiniPanacheContext#columnInfos}) to map each entity
 * field/record component to a SQL column. All SQL statements are built once per
 * repository instance (at construction time) and reused for every operation.
 *
 * <h2>Transaction participation</h2>
 * Each operation checks {@link TransactionManager#currentConnection()}:
 * <ul>
 *   <li>If a transaction is active → uses its connection (participates in the transaction)</li>
 *   <li>Otherwise → borrows a connection from the datasource with auto-commit (own transaction)</li>
 * </ul>
 * This mirrors Hibernate's connection-borrowing strategy.
 *
 * <h2>Extending with custom finders</h2>
 * <pre>{@code
 * @Singleton
 * public class ProductRepository extends MiniRepositoryBase<Product> {
 *     public ProductRepository() { super(Product.class); }
 *
 *     public List<Product> findByName(String name) {
 *         return findWhere("name = ?", name);
 *     }
 *
 *     public List<Product> findCheaperThan(BigDecimal maxPrice) {
 *         return findWhere("price < ?", maxPrice);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the entity type; must be annotated with {@link MiniEntity}
 */
public abstract class MiniRepositoryBase<T> implements MiniRepository<T> {

    private static final Logger LOG = Logger.getLogger(MiniRepositoryBase.class);

    private final Class<T> entityClass;
    private final String tableName;
    private final List<MiniPanacheContext.ColumnInfo> columns;
    private final MiniPanacheContext.ColumnInfo idColumn;

    // Pre-built SQL — mirrors Hibernate's SQL generation done at SessionFactory build time
    private final String selectAll;
    private final String selectById;
    private final String insert;
    private final String update;
    private final String deleteById;
    private final String count;

    protected MiniRepositoryBase(Class<T> entityClass) {
        this.entityClass = entityClass;
        MiniEntity ann = entityClass.getAnnotation(MiniEntity.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                    entityClass.getName() + " must be annotated with @MiniEntity");
        }
        this.tableName = MiniPanacheContext.resolveTableName(entityClass, ann);
        this.columns = MiniPanacheContext.columnInfos(entityClass);
        this.idColumn = columns.stream()
                .filter(MiniPanacheContext.ColumnInfo::isId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        entityClass.getName() + " must have exactly one @Id field"));

        // Build SQL once at construction time (not per-operation)
        this.selectAll   = buildSelectAll();
        this.selectById  = buildSelectById();
        this.insert      = buildInsert();
        this.update      = buildUpdate();
        this.deleteById  = buildDeleteById();
        this.count       = "SELECT COUNT(*) FROM " + tableName;

        LOG.debugf("[panache] %s repository ready (table=%s)", entityClass.getSimpleName(), tableName);
    }

    // -------------------------------------------------------------------------
    // MiniRepository implementation
    // -------------------------------------------------------------------------

    @Override
    public Optional<T> findById(long id) {
        LOG.debugf("[panache] %s.findById(%d)", entityClass.getSimpleName(), id);
        return withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(selectById)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<T> findAll() {
        LOG.debugf("[panache] %s.findAll()", entityClass.getSimpleName());
        return withConnection(conn -> {
            List<T> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(selectAll);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
            return result;
        });
    }

    @Override
    public T persist(T entity) {
        LOG.debugf("[panache] %s.persist()", entityClass.getSimpleName());
        return withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                int idx = 1;
                for (MiniPanacheContext.ColumnInfo col : columns) {
                    if (col.isId()) continue; // skip @Id — auto-generated
                    ps.setObject(idx++, getFieldValue(entity, col.fieldName()));
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return withId(entity, keys.getLong(1));
                    }
                }
                return entity;
            }
        });
    }

    @Override
    public T update(T entity) {
        LOG.debugf("[panache] %s.update()", entityClass.getSimpleName());
        return withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                int idx = 1;
                for (MiniPanacheContext.ColumnInfo col : columns) {
                    if (col.isId()) continue;
                    ps.setObject(idx++, getFieldValue(entity, col.fieldName()));
                }
                // WHERE id = ?
                ps.setLong(idx, toLong(getFieldValue(entity, idColumn.fieldName())));
                ps.executeUpdate();
                return entity;
            }
        });
    }

    @Override
    public void delete(T entity) {
        deleteById(toLong(getFieldValue(entity, idColumn.fieldName())));
    }

    @Override
    public void deleteById(long id) {
        LOG.debugf("[panache] %s.deleteById(%d)", entityClass.getSimpleName(), id);
        withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(deleteById)) {
                ps.setLong(1, id);
                ps.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public long count() {
        return withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(count);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Extension point: custom WHERE finders
    // -------------------------------------------------------------------------

    /**
     * Finds all entities matching the given WHERE clause.
     *
     * <pre>{@code
     * findWhere("name = ? AND price < ?", "Widget", new BigDecimal("9.99"))
     * }</pre>
     *
     * Mirrors Panache's {@code find("name = ?1 and price < ?2", name, maxPrice)}.
     */
    protected List<T> findWhere(String whereClause, Object... params) {
        String sql = selectAll + " WHERE " + whereClause;
        return withConnection(conn -> {
            List<T> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            }
            return result;
        });
    }

    // -------------------------------------------------------------------------
    // SQL building
    // -------------------------------------------------------------------------

    private String buildSelectAll() {
        return "SELECT * FROM " + tableName;
    }

    private String buildSelectById() {
        return "SELECT * FROM " + tableName + " WHERE " + idColumn.columnName() + " = ?";
    }

    private String buildInsert() {
        // INSERT INTO table (col1, col2, ...) VALUES (?, ?, ...)
        List<String> colNames = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (MiniPanacheContext.ColumnInfo col : columns) {
            if (col.isId()) continue; // skip @Id
            colNames.add(col.columnName());
            placeholders.add("?");
        }
        return "INSERT INTO " + tableName
                + " (" + String.join(", ", colNames) + ")"
                + " VALUES (" + String.join(", ", placeholders) + ")";
    }

    private String buildUpdate() {
        // UPDATE table SET col1=?, col2=? WHERE id=?
        List<String> setClauses = new ArrayList<>();
        for (MiniPanacheContext.ColumnInfo col : columns) {
            if (col.isId()) continue;
            setClauses.add(col.columnName() + " = ?");
        }
        return "UPDATE " + tableName
                + " SET " + String.join(", ", setClauses)
                + " WHERE " + idColumn.columnName() + " = ?";
    }

    private String buildDeleteById() {
        return "DELETE FROM " + tableName + " WHERE " + idColumn.columnName() + " = ?";
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    /** Gets the value of a named field or record component from an entity instance. */
    private Object getFieldValue(T entity, String fieldName) {
        try {
            if (entity.getClass().isRecord()) {
                // Records expose components as accessor methods with the same name
                return entity.getClass().getMethod(fieldName).invoke(entity);
            } else {
                java.lang.reflect.Field f = entity.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot read field '" + fieldName + "' from " + entity, e);
        }
    }

    /** Maps a ResultSet row to an entity instance. */
    private T mapRow(ResultSet rs) {
        try {
            if (entityClass.isRecord()) {
                return mapRowToRecord(rs);
            } else {
                return mapRowToBean(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to map row to " + entityClass.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private T mapRowToRecord(ResultSet rs) throws Exception {
        RecordComponent[] components = entityClass.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            MiniPanacheContext.ColumnInfo info = findColumnInfo(components[i].getName());
            values[i] = readColumn(rs, info.columnName(), types[i]);
        }
        Constructor<T> ctor = (Constructor<T>) entityClass.getDeclaredConstructor(types);
        return ctor.newInstance(values);
    }

    @SuppressWarnings("unchecked")
    private T mapRowToBean(ResultSet rs) throws Exception {
        T instance = (T) entityClass.getDeclaredConstructor().newInstance();
        for (MiniPanacheContext.ColumnInfo info : columns) {
            java.lang.reflect.Field f = entityClass.getDeclaredField(info.fieldName());
            f.setAccessible(true);
            f.set(instance, readColumn(rs, info.columnName(), info.javaType()));
        }
        return instance;
    }

    private Object readColumn(ResultSet rs, String colName, Class<?> type) throws SQLException {
        if (type == String.class)  return rs.getString(colName);
        if (type == Long.class || type == long.class) return rs.getLong(colName);
        if (type == Integer.class || type == int.class) return rs.getInt(colName);
        if (type == Boolean.class || type == boolean.class) return rs.getBoolean(colName);
        if (type == BigDecimal.class) return rs.getBigDecimal(colName);
        if (type == LocalDate.class) {
            Date d = rs.getDate(colName);
            return d != null ? d.toLocalDate() : null;
        }
        if (type == LocalDateTime.class) {
            Timestamp ts = rs.getTimestamp(colName);
            return ts != null ? ts.toLocalDateTime() : null;
        }
        if (type == Double.class || type == double.class) return rs.getDouble(colName);
        if (type == Float.class || type == float.class)   return rs.getFloat(colName);
        return rs.getObject(colName);
    }

    /** Returns a new entity instance identical to {@code entity} but with the given id set. */
    @SuppressWarnings("unchecked")
    private T withId(T entity, long newId) {
        try {
            if (entityClass.isRecord()) {
                RecordComponent[] components = entityClass.getRecordComponents();
                Class<?>[] types = new Class<?>[components.length];
                Object[] values = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    types[i] = components[i].getType();
                    if (components[i].getName().equals(idColumn.fieldName())) {
                        values[i] = newId;
                    } else {
                        values[i] = components[i].getAccessor().invoke(entity);
                    }
                }
                Constructor<T> ctor = (Constructor<T>) entityClass.getDeclaredConstructor(types);
                return ctor.newInstance(values);
            } else {
                // For plain classes: set the @Id field
                T copy = entity; // mutate in place (not ideal but simple)
                java.lang.reflect.Field f = entityClass.getDeclaredField(idColumn.fieldName());
                f.setAccessible(true);
                f.set(copy, newId);
                return copy;
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot set @Id on " + entity, e);
        }
    }

    private MiniPanacheContext.ColumnInfo findColumnInfo(String fieldName) {
        return columns.stream()
                .filter(c -> c.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No column info for field: " + fieldName));
    }

    private static long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        throw new IllegalArgumentException("Cannot convert " + value + " to long");
    }

    // -------------------------------------------------------------------------
    // Connection management — transaction-aware
    // -------------------------------------------------------------------------

    /** Executes {@code operation} using either the active transaction connection or auto-commit. */
    private <R> R withConnection(SqlOperation<R> operation) {
        Connection txConn = TransactionManager.currentConnection();
        if (txConn != null) {
            // Participate in the existing transaction
            try {
                return operation.execute(txConn);
            } catch (SQLException e) {
                throw new RuntimeException("Database error in " + entityClass.getSimpleName(), e);
            }
        }
        // No active transaction — use auto-commit connection
        try (Connection conn = MiniPanacheContext.getDataSource().getConnection()) {
            conn.setAutoCommit(true);
            return operation.execute(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Database error in " + entityClass.getSimpleName(), e);
        }
    }

    @FunctionalInterface
    private interface SqlOperation<R> {
        R execute(Connection connection) throws SQLException;
    }
}
