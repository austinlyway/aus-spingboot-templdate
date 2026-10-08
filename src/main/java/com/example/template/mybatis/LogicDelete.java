package com.example.template.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a soft-delete / logical-delete flag.
 *
 * <p>The field type may be any of the following:
 * <ul>
 *   <li>{@code Integer / int} — values 0 / non-zero (typical for {@code deleted(0)}).</li>
 *   <li>{@code Boolean / boolean} — false / true.</li>
 * </ul>
 *
 * <p>When set, the {@link BaseMapper} automatically:
 * <ul>
 *   <li>Filters out "deleted" rows from every SELECT.</li>
 *   <li>Converts {@code deleteById} into an UPDATE that flips the flag instead of
 *       a hard DELETE.</li>
 *   <li>Initialises the flag to {@link #notDeletedValue()} on insert.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LogicDelete {

    /**
     * The "active" / "not deleted" value. The opposite is used for marking rows
     * as deleted. Default {@code 0}, which matches the common {@code deleted TINYINT(1)}
     * convention.
     */
    int notDeletedValue() default 0;

    /**
     * The "deleted" value written by soft-delete. Default {@code 1}.
     */
    int deletedValue() default 1;
}