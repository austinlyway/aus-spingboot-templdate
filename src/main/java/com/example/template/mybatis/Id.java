package com.example.template.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the primary key.
 *
 * <p>Exactly one field per entity must carry this annotation. The {@link BaseMapper}
 * uses it to build WHERE clauses for {@code selectById} / {@code updateById} /
 * {@code deleteById}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
}