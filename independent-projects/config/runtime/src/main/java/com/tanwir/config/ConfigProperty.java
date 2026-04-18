package com.tanwir.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injection point for configuration properties.
 * Similar to MicroProfile Config @ConfigProperty.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigProperty {
    
    /**
     * The name of the configuration property.
     * If not specified, defaults to the field/parameter name.
     */
    String name() default "";
    
    /**
     * The default value to use if the property is not found.
     */
    String defaultValue() default "";
}
