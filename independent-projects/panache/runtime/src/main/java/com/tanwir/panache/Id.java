package com.tanwir.panache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the primary key of a {@link MiniEntity}.
 *
 * <p>Mirrors {@code jakarta.persistence.Id}. Exactly one field per entity must carry
 * this annotation. The column is generated as {@code BIGINT AUTO_INCREMENT PRIMARY KEY}
 * and is populated automatically on {@link MiniRepositoryBase#persist persist}.
 *
 * <pre>{@code
 * @MiniEntity
 * public record Product(@Id Long id, String name, BigDecimal price) {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Id {
}
