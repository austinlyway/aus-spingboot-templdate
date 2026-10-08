package com.example.template.mybatis;

import java.util.List;

/**
 * Single-table CRUD mapper that builds SQL dynamically from the entity's
 * annotations ({@link Table}, {@link Column}, {@link Id}, {@link LogicDelete},
 * {@link CreatedAt}, {@link UpdatedAt}, {@link CreatedBy}, {@link UpdatedBy}).
 *
 * <p>Usage:
 * <pre>
 * &#64;Mapper
 * public interface UserMapper extends BaseMapper&lt;User&gt; { }
 * </pre>
 *
 * <p>How it works:
 * <ol>
 *   <li>The actual MyBatis-bound abstract methods (with {@code @XxxProvider}
 *       annotations) live on {@link MapperBridge}. The user's mapper interface
 *       inherits them via {@code extends BaseMapper<T>}.</li>
 *   <li>The MyBatis-generated proxy implements those abstract methods. The
 *       default methods on this interface (e.g. {@link #insert}) call them via
 *       {@code this.<bridgeMethod>(...)}; on the proxy that dispatches to
 *       {@link SqlProvider}.</li>
 *   <li>Before dispatching, each default method pushes the entity {@code T} onto
 *       a thread-local so the shared {@link SqlProvider} can build type-correct
 *       SQL.</li>
 *   <li>Audit / lifecycle fields are auto-populated on insert and update via
 *       {@link AuditorProvider}. Override the default with
 *       {@link #setAuditorProvider(AuditorProvider)} at startup.</li>
 * </ol>
 */
public interface BaseMapper<T> extends MapperBridge {

    // ------------------------------------------------------------------
    // Public API — the only methods callers should invoke.
    // ------------------------------------------------------------------

    default int insert(T entity) {
        return SqlProvider.runWithEntity(this, () -> {
            AuditorProvider auditor = SqlProvider.auditor();
            // Stamp audit fields on the entity so callers see the same values
            // we wrote. Database default values or triggers are not consulted
            // here — keeping the round-trip explicit and portable.
            SqlProvider.applyAuditFields(entity, true, auditor);
            return this.bridgeInsert(entity, auditor.now(), auditor.currentUser());
        });
    }

    default int updateById(T entity) {
        return SqlProvider.runWithEntity(this, () -> {
            AuditorProvider auditor = SqlProvider.auditor();
            SqlProvider.applyAuditFields(entity, false, auditor);
            return this.bridgeUpdateById(entity, auditor.now(), auditor.currentUser());
        });
    }

    default int hardDeleteById(Object id) {
        return SqlProvider.runWithEntity(this, () -> this.bridgeHardDeleteById(id));
    }

    default int deleteById(Object id) {
        return SqlProvider.runWithEntity(this, () -> {
            AuditorProvider auditor = SqlProvider.auditor();
            return this.bridgeDeleteById(id, auditor.now(), auditor.currentUser());
        });
    }

    default T selectById(Object id) {
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows =
                (List<java.util.Map<String, Object>>) SqlProvider.runWithEntity(
                        this, () -> this.bridgeSelectById(id));
        if (rows.isEmpty()) {
            return null;
        }
        return (T) SqlProvider.mapToEntity(rows.get(0), SqlProvider.currentEntity(this));
    }

    default List<T> selectAll() {
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows =
                (List<java.util.Map<String, Object>>) SqlProvider.runWithEntity(
                        this, this::bridgeSelectAll);
        return (List<T>) SqlProvider.mapsToEntities(rows, SqlProvider.currentEntity(this));
    }

    default List<T> selectByIds(List<? extends Object> ids) {
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows =
                (List<java.util.Map<String, Object>>) SqlProvider.runWithEntity(
                        this, () -> this.bridgeSelectByIds((List<?>) ids));
        return (List<T>) SqlProvider.mapsToEntities(rows, SqlProvider.currentEntity(this));
    }

    default long count() {
        return SqlProvider.runWithEntity(this, this::bridgeCount);
    }

    // ------------------------------------------------------------------
    // Audit / lifecycle override
    // ------------------------------------------------------------------

    /**
     * Replace the audit provider used by all {@code BaseMapper} implementations.
     * Call once at application startup.
     */
    static void setAuditorProvider(AuditorProvider provider) {
        SqlProvider.setAuditorProvider(provider);
    }
}