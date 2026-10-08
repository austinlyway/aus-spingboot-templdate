package com.example.template.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a MyBatis entity and binds it to a database table.
 *
 * <p>Required on every entity that participates in {@link BaseMapper}-driven CRUD.
 *
 * <p>Example:
 * <pre>
 * &#64;Table("users")
 * public class User { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {

    /** The database table name. */
    String value();
}