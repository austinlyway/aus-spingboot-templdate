package com.example.template.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the row creator identifier (e.g. user id or username).
 *
 * <p>When set, the {@link BaseMapper} populates this field automatically on insert
 * with the value supplied by the {@link AuditorProvider}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CreatedBy {
}