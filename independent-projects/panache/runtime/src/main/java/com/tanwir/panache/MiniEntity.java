package com.tanwir.panache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or record as a persistence entity.
 *
 * <p>Mirrors {@code jakarta.persistence.Entity} from the JPA spec, and is analogous to
 * annotating a Quarkus Panache entity with {@code @Entity}. At startup the
 * {@link MiniPanacheContext} scans for all classes registered with this annotation and
 * issues {@code CREATE TABLE IF NOT EXISTS} DDL — mirroring Hibernate's schema generation.
 *
 * <h2>Field mapping conventions</h2>
 * Fields are mapped to columns with these defaults:
 * <ul>
 *   <li>Field name → column name (snake_case conversion: {@code createdAt} → {@code created_at})</li>
 *   <li>{@link Id}-annotated field → {@code PRIMARY KEY AUTO_INCREMENT}</li>
 *   <li>Use {@link Column} to override the column name</li>
 * </ul>
 *
 * <h2>Supported Java types</h2>
 * <table>
 *   <tr><th>Java type</th><th>SQL type</th></tr>
 *   <tr><td>{@code String}</td><td>{@code VARCHAR(255)}</td></tr>
 *   <tr><td>{@code Long}, {@code long}</td><td>{@code BIGINT}</td></tr>
 *   <tr><td>{@code Integer}, {@code int}</td><td>{@code INTEGER}</td></tr>
 *   <tr><td>{@code Boolean}, {@code boolean}</td><td>{@code BOOLEAN}</td></tr>
 *   <tr><td>{@code java.math.BigDecimal}</td><td>{@code DECIMAL(19,2)}</td></tr>
 *   <tr><td>{@code java.time.LocalDate}</td><td>{@code DATE}</td></tr>
 *   <tr><td>{@code java.time.LocalDateTime}</td><td>{@code TIMESTAMP}</td></tr>
 * </table>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @MiniEntity
 * public record Product(
 *     @Id Long id,
 *     String name,
 *     @Column("unit_price") BigDecimal price) {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MiniEntity {

    /**
     * SQL table name. Defaults to the simple class name lowercased.
     * E.g. {@code Product} → {@code product}.
     */
    String tableName() default "";
}
