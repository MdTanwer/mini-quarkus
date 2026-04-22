package com.tanwir.panache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the SQL column name for a field.
 *
 * <p>Mirrors {@code jakarta.persistence.Column}. Without this annotation the column name
 * defaults to the field name converted to snake_case
 * (e.g. {@code unitPrice} → {@code unit_price}).
 *
 * <pre>{@code
 * @MiniEntity
 * public record Product(@Id Long id,
 *                       String name,
 *                       @Column("unit_price") BigDecimal price) {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Column {

    /** The SQL column name. Defaults to the field name (snake_case converted). */
    String value() default "";
}
