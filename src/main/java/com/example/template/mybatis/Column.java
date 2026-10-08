package com.example.template.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the default column-name mapping for a field.
 *
 * <p>Default mapping is camelCase → snake_case (e.g. {@code createdAt} →
 * {@code created_at}). Use this annotation when the column name differs.
 *
 * <p>Example:
 * <pre>
 * &#64;Table("users")
 * public class User {
 *     &#64;Column("user_name")
 *     private String username;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /** The database column name. */
    String value();
}