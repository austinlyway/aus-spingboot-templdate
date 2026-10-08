package com.example.template.mybatis;

import java.time.LocalDateTime;

/**
 * Supplies the values that {@link BaseMapper} writes into audit / lifecycle
 * fields (created-at, updated-at, created-by, updated-by).
 *
 * <p>Implementations typically pull "current user" from a Spring
 * {@code SecurityContext} or thread-local. The static default implementation
 * uses {@code LocalDateTime.now()} and a fixed {@code "system"} user — fine for
 * batch jobs, tests, and background workers.
 */
public interface AuditorProvider {

    /** Value written to a {@link CreatedAt} / {@link UpdatedAt} field. */
    LocalDateTime now();

    /**
     * Value written to a {@link CreatedBy} / {@link UpdatedBy} field. May be any
     * type the field declares (typically {@code Long} or {@code String}).
     */
    Object currentUser();

    /**
     * Built-in fallback: timestamps come from the system clock, the user is
     * the literal string {@code "system"}.
     */
    AuditorProvider SYSTEM = new AuditorProvider() {
        @Override
        public LocalDateTime now() {
            return LocalDateTime.now();
        }

        @Override
        public Object currentUser() {
            return "system";
        }
    };
}