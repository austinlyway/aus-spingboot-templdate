package com.example.template.mybatis;

import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * MyBatis {@code @SqlProvider} that turns an entity's metadata into
 * parameterised SQL.
 *
 * <p>The static methods here are referenced by name from {@link MapperBridge}'s
 * {@code @XxxProvider} annotations. They rely on a thread-local
 * ({@link #LAST_ENTITY_TYPE}) populated by {@link BaseMapper}'s default methods
 * to know the entity type for the current invocation.
 */
public final class SqlProvider {

    private static final java.util.concurrent.atomic.AtomicReference<AuditorProvider> AUDITOR =
            new java.util.concurrent.atomic.AtomicReference<>(AuditorProvider.SYSTEM);

    /**
     * The entity type for the current MyBatis invocation. Populated by
     * {@link BaseMapper}'s default methods before MyBatis routes to a provider
     * method, and cleared in a finally block.
     */
    static final ThreadLocal<Class<?>> LAST_ENTITY_TYPE = new ThreadLocal<>();

    private SqlProvider() {
    }

    public static void setAuditorProvider(AuditorProvider provider) {
        AUDITOR.set(provider == null ? AuditorProvider.SYSTEM : provider);
    }

    /** Read-only view of the current {@link AuditorProvider}. */
    public static AuditorProvider auditor() {
        return AUDITOR.get();
    }

    /**
     * Stamp audit fields on the given entity so the caller sees the same values
     * that will be written to the database. Used by {@link BaseMapper#insert}
     * to keep the in-memory entity consistent with the persisted row.
     *
     * @param entity      the entity to mutate
     * @param isInsert    true on insert (stamps {@code created_at} and
     *                    {@code created_by}); false on update (those fields are
     *                    preserved)
     * @param auditor     the source of the values
     */
    public static void applyAuditFields(Object entity, boolean isInsert, AuditorProvider auditor) {
        EntityMeta meta = EntityMeta.of(entity.getClass());
        java.time.LocalDateTime now = auditor.now();
        Object user = auditor.currentUser();
        for (EntityField f : meta.getInsertableFields()) {
            if (isInsert) {
                if (f.isCreatedAt() || f.isUpdatedAt()) {
                    f.setValue(entity, now);
                } else if (f.isCreatedBy() || f.isUpdatedBy()) {
                    f.setValue(entity, user);
                } else if (f.isLogicDelete()) {
                    f.setValue(entity, f.getLogicDeleteActiveValue());
                }
            } else {
                if (f.isUpdatedAt()) {
                    f.setValue(entity, now);
                } else if (f.isUpdatedBy()) {
                    f.setValue(entity, user);
                }
            }
        }
    }

    /**
     * Run a {@code BaseMapper} operation under the supplied thread-local. The
     * entity type is published to the provider so it can build type-correct
     * SQL. The default methods on {@link BaseMapper} are the only callers of
     * this helper.
     */
    public static <R> R runWithEntity(BaseMapper<?> mapper, java.util.function.Supplier<R> action) {
        Class<?> entityType = entityTypeOf(mapper);
        boolean restore = false;
        try {
            if (LAST_ENTITY_TYPE.get() == null) {
                LAST_ENTITY_TYPE.set(entityType);
                restore = true;
            }
            return action.get();
        } finally {
            if (restore) {
                LAST_ENTITY_TYPE.remove();
            }
        }
    }

    /**
     * Resolve the entity {@link Class} from a {@code BaseMapper<?>} by walking
     * its generic superinterfaces.
     */
    static Class<?> entityTypeOf(BaseMapper<?> mapper) {
        // JDK dynamic proxies don't carry generic interface info. Instead, look at
        // every interface the proxy implements, and for each one, inspect the
        // (loaded) interface class's own generic superinterfaces. This works because
        // when the interface is loaded by the same classloader, its generic
        // parameterisation is preserved.
        for (Class<?> iface : mapper.getClass().getInterfaces()) {
            Class<?> entity = extractFromInterface(iface);
            if (entity != null) {
                return entity;
            }
        }
        // Fallback: walk the proxy's superclass.
        Class<?> current = mapper.getClass().getSuperclass();
        while (current != null && current != Object.class) {
            for (Class<?> iface : current.getInterfaces()) {
                Class<?> entity = extractFromInterface(iface);
                if (entity != null) {
                    return entity;
                }
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException(
                "Cannot resolve entity type for mapper " + mapper.getClass().getName());
    }

    private static Class<?> extractFromInterface(Class<?> iface) {
        if (iface == null || iface == BaseMapper.class) {
            return null;
        }
        for (java.lang.reflect.Type t : iface.getGenericInterfaces()) {
            Class<?> entity = extractFromType(t);
            if (entity != null) {
                return entity;
            }
        }
        // Recurse up the interface hierarchy in case the user's interface extends
        // another non-BaseMapper interface that itself extends BaseMapper.
        Class<?> entity = extractFromInterface(iface.getSuperclass());
        if (entity != null) {
            return entity;
        }
        for (Class<?> parentIface : iface.getInterfaces()) {
            entity = extractFromInterface(parentIface);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static Class<?> extractFromType(java.lang.reflect.Type t) {
        if (!(t instanceof java.lang.reflect.ParameterizedType pt)) {
            return null;
        }
        java.lang.reflect.Type raw = pt.getRawType();
        if (!(raw instanceof Class<?> rawClass) || rawClass != BaseMapper.class) {
            return null;
        }
        java.lang.reflect.Type typeArg = pt.getActualTypeArguments()[0];
        if (typeArg instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    /**
     * Resolve the entity class for a mapper instance. Convenience for
     * {@link #entityTypeOf(BaseMapper)}.
     */
    public static Class<?> currentEntity(BaseMapper<?> mapper) {
        return entityTypeOf(mapper);
    }

    /**
     * Convert a single row (a map of column → value) to an entity instance by
     * matching column names to {@link EntityField}s.
     */
    public static <T> T mapToEntity(java.util.Map<String, Object> row, Class<T> entityClass) {
        if (row == null) {
            return null;
        }
        EntityMeta meta = EntityMeta.of(entityClass);
        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();
            for (EntityField f : meta.getSelectableFields()) {
                Object value = row.get(f.getColumnName());
                if (value == null) {
                    // Try case-insensitive lookup as a fallback.
                    for (java.util.Map.Entry<String, Object> e : row.entrySet()) {
                        if (e.getKey() != null && e.getKey().equalsIgnoreCase(f.getColumnName())) {
                            value = e.getValue();
                            break;
                        }
                    }
                }
                if (value != null) {
                    f.setValue(entity, coerce(value, f.getType()));
                }
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot instantiate " + entityClass.getName() + " (no-arg constructor required)", e);
        }
    }

    /**
     * Convert a list of rows to a list of entity instances.
     */
    public static <T> List<T> mapsToEntities(List<java.util.Map<String, Object>> rows, Class<T> entityClass) {
        if (rows == null || rows.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<T> out = new java.util.ArrayList<>(rows.size());
        for (java.util.Map<String, Object> row : rows) {
            out.add(mapToEntity(row, entityClass));
        }
        return out;
    }

    /**
     * Coerce a raw SQL value into the entity field's declared type, handling the
     * common JDBC type mismatches (e.g. {@code java.sql.Timestamp} → {@code LocalDateTime},
     * {@code Integer} → {@code Long}). Exposed for {@link SingleEntityMapper}.
     */
    @SuppressWarnings("unchecked")
    public static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }
        // MySQL/H2 often return timestamps as java.sql.Timestamp.
        if (target == java.time.LocalDateTime.class && value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (target == java.time.LocalDate.class && value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (target == java.time.LocalTime.class && value instanceof java.sql.Time t) {
            return t.toLocalTime();
        }
        // Integer / Long / Double / String widening.
        if (Number.class.isAssignableFrom(target) && value instanceof Number n) {
            if (target == Long.class) return n.longValue();
            if (target == Integer.class) return n.intValue();
            if (target == Short.class) return n.shortValue();
            if (target == Byte.class) return n.byteValue();
            if (target == Double.class) return n.doubleValue();
            if (target == Float.class) return n.floatValue();
        }
        if (target == String.class) {
            return value.toString();
        }
        if (target.isEnum() && value instanceof String s) {
            return Enum.valueOf((Class<Enum>) target.asSubclass(Enum.class), s);
        }
        // Last resort: let BeanUtils / reflection try the assignment.
        return value;
    }

    public static String insert(java.util.Map<String, Object> params) {
        // MyBatis passes a ParamMap because of @Param("entity"). Pull the entity out.
        Object entity = params.get("entity");
        return buildInsert(entity.getClass());
    }

    public static String updateById(java.util.Map<String, Object> params) {
        Object entity = params.get("entity");
        return buildUpdateById(entity.getClass());
    }

    public static String deleteById(java.util.Map<String, Object> params) {
        return buildDeleteById(currentEntity(), false);
    }

    public static String hardDeleteById(java.util.Map<String, Object> params) {
        return buildDeleteById(currentEntity(), true);
    }

    public static String selectById(java.util.Map<String, Object> params) {
        return buildSelectById(currentEntity());
    }

    public static String selectAll() {
        return buildSelectAll(currentEntity());
    }

    public static String selectByIds(java.util.Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) params.get("ids");
        return buildSelectByIds(currentEntity(), ids == null ? 0 : ids.size());
    }

    public static String count() {
        return buildCount(currentEntity());
    }

    private static Class<?> currentEntity() {
        Class<?> c = LAST_ENTITY_TYPE.get();
        if (c == null) {
            throw new IllegalStateException(
                    "No entity type registered for this MyBatis invocation. "
                            + "Did you call a BaseMapper default method on a non-BaseMapper mapper?");
        }
        return c;
    }

    // ------------------------------------------------------------------
    // SQL builders
    // ------------------------------------------------------------------

    static String buildInsert(Class<?> entityClass) {
        return buildInsert(entityClass, true);
    }

    static String buildInsert(Class<?> entityClass, boolean paramWrapped) {
        return buildInsert(entityClass, paramWrapped, false);
    }

    /**
     * @param paramWrapped        {@code true} for {@code BaseMapper} where the entity is
     *                            passed as a top-level parameter named {@code entity}.
     *                            {@code false} for {@code SingleEntityMapper} where the
     *                            entity is the top-level parameter directly.
     * @param useEntityAuditNames when {@code true}, the audit placeholders reference
     *                            the entity's own audit fields (e.g. {@code #{createdAt}}).
     *                            This is used when the entity is passed as the
     *                            top-level parameter and we want MyBatis to write
     *                            the generated key back to {@code entity.id}.
     */
    static String buildInsert(Class<?> entityClass, boolean paramWrapped, boolean useEntityAuditNames) {
        EntityMeta meta = EntityMeta.of(entityClass);
        SQL sql = new SQL().INSERT_INTO(meta.getTableName());
        for (EntityField f : meta.getInsertableFields()) {
            if (f.isCreatedAt() || f.isUpdatedAt()) {
                sql.VALUES(f.getColumnName(), auditPlaceholder(f, useEntityAuditNames, "now"));
            } else if (f.isCreatedBy() || f.isUpdatedBy()) {
                sql.VALUES(f.getColumnName(), auditPlaceholder(f, useEntityAuditNames, "user"));
            } else if (f.isLogicDelete()) {
                sql.VALUES(f.getColumnName(), String.valueOf(f.getLogicDeleteActiveValue()));
            } else {
                sql.VALUES(f.getColumnName(), placeholder(f.getName(), paramWrapped));
            }
        }
        return sql.toString();
    }

    static String buildUpdateById(Class<?> entityClass) {
        return buildUpdateById(entityClass, true);
    }

    static String buildUpdateById(Class<?> entityClass, boolean paramWrapped) {
        return buildUpdateById(entityClass, paramWrapped, false);
    }

    static String buildUpdateById(Class<?> entityClass, boolean paramWrapped, boolean useEntityAuditNames) {
        EntityMeta meta = EntityMeta.of(entityClass);
        SQL sql = new SQL().UPDATE(meta.getTableName());
        for (EntityField f : meta.getUpdatableFields()) {
            if (f.isUpdatedAt()) {
                sql.SET(f.getColumnName() + " = " + auditPlaceholder(f, useEntityAuditNames, "now"));
            } else if (f.isUpdatedBy()) {
                sql.SET(f.getColumnName() + " = " + auditPlaceholder(f, useEntityAuditNames, "user"));
            } else {
                sql.SET(f.getColumnName() + " = " + placeholder(f.getName(), paramWrapped));
            }
        }
        sql.WHERE(meta.getIdField().getColumnName() + " = "
                + placeholder(meta.getIdField().getName(), paramWrapped));
        return sql.toString();
    }

    /**
     * Returns the {@code #{...}} placeholder for an audit field.
     *
     * @param field            the audit field on the entity
     * @param useEntityNames   if {@code true}, reference the entity field directly
     *                         (e.g. {@code #{createdAt}}). Otherwise use the
     *                         separate top-level {@code #{audit_now}} /
     *                         {@code #{audit_user}} (the {@code BaseMapper} mode).
     * @param which            either {@code "now"} or {@code "user"}
     */
    private static String auditPlaceholder(EntityField field, boolean useEntityNames, String which) {
        if (useEntityNames) {
            return "#{" + field.getName() + "}";
        }
        return which.equals("now") ? "#{audit_now}" : "#{audit_user}";
    }

    static String buildDeleteById(Class<?> entityClass, boolean hard) {
        EntityMeta meta = EntityMeta.of(entityClass);
        if (hard || !meta.hasLogicDelete()) {
            return "DELETE FROM " + meta.getTableName()
                    + " WHERE " + meta.getIdField().getColumnName() + " = #{id}";
        }
        StringBuilder sb = new StringBuilder("UPDATE ").append(meta.getTableName()).append(" SET ");
        sb.append(meta.getLogicDeleteField().getColumnName())
                .append(" = ").append(meta.getLogicDeleteField().getLogicDeleteDeletedValue());
        for (EntityField f : meta.getUpdatableFields()) {
            if (f.isUpdatedAt()) {
                sb.append(", ").append(f.getColumnName()).append(" = #{audit_now}");
            } else if (f.isUpdatedBy()) {
                sb.append(", ").append(f.getColumnName()).append(" = #{audit_user}");
            }
        }
        sb.append(" WHERE ").append(meta.getIdField().getColumnName()).append(" = #{id}");
        return sb.toString();
    }

    static String buildSelectById(Class<?> entityClass) {
        EntityMeta meta = EntityMeta.of(entityClass);
        return new SQL() {{
            SELECT(columnList(meta));
            FROM(meta.getTableName());
            applySoftDeleteFilter(this, meta);
            WHERE(meta.getIdField().getColumnName() + " = #{id}");
        }}.toString();
    }

    static String buildSelectAll(Class<?> entityClass) {
        EntityMeta meta = EntityMeta.of(entityClass);
        return new SQL() {{
            SELECT(columnList(meta));
            FROM(meta.getTableName());
            applySoftDeleteFilter(this, meta);
        }}.toString();
    }

    static String buildSelectByIds(Class<?> entityClass, int count) {
        return buildSelectByIds(entityClass, count, "ids");
    }

    /**
     * @param paramName the placeholder name for the list parameter
     *                   (e.g. {@code "ids"} for the @Param-bound BaseMapper,
     *                   {@code "list"} for the implicit-name SingleEntityMapper)
     */
    static String buildSelectByIds(Class<?> entityClass, int count, String paramName) {
        EntityMeta meta = EntityMeta.of(entityClass);
        return new SQL() {{
            SELECT(columnList(meta));
            FROM(meta.getTableName());
            applySoftDeleteFilter(this, meta);
            WHERE(meta.getIdField().getColumnName() + " IN (" + placeholders(count, paramName) + ")");
        }}.toString();
    }

    /**
     * Audit field placeholders — referenced directly by name from the
     * {@link com.example.template.mybatis.SingleEntityMapper.InsertParams}
     * parameter object (camelCase Java fields).
     */
    public static final String AUDIT_NOW_PLACEHOLDER = "#{auditNow}";
    public static final String AUDIT_USER_PLACEHOLDER = "#{auditUser}";

    /**
     * Build a {@code #{...}} placeholder for a property. When {@code paramWrapped}
     * is {@code true} the property is accessed via {@code entity.foo}; otherwise
     * directly via {@code foo} (top-level parameter binding).
     */
    private static String placeholder(String propertyName, boolean paramWrapped) {
        return paramWrapped ? "#{entity." + propertyName + "}" : "#{" + propertyName + "}";
    }

    static String buildCount(Class<?> entityClass) {
        return buildCount(entityClass, "*");
    }

    /**
     * @param countColumn what to count — defaults to {@code "*"}, but can be
     *                     overridden (e.g. to a specific column).
     */
    static String buildCount(Class<?> entityClass, String countColumn) {
        EntityMeta meta = EntityMeta.of(entityClass);
        return new SQL() {{
            // Alias to a stable column name so callers can map the result.
            SELECT("COUNT(" + countColumn + ") AS count");
            FROM(meta.getTableName());
            applySoftDeleteFilter(this, meta);
        }}.toString();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static String columnList(EntityMeta meta) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (EntityField f : meta.getSelectableFields()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(f.getColumnName());
            first = false;
        }
        return sb.toString();
    }

    private static void applySoftDeleteFilter(SQL sql, EntityMeta meta) {
        if (meta.hasLogicDelete()) {
            sql.WHERE(meta.getLogicDeleteField().getColumnName() + " = "
                    + meta.getLogicDeleteField().getLogicDeleteActiveValue());
        }
    }

    /**
     * Build a simple {@link ResultMap} that maps a single scalar result column
     * (e.g. {@code COUNT(*)}) to a {@link Long}. Used by {@code count()}.
     */
    public static ResultMap buildScalarLongResultMap(Configuration configuration, String statementId) {
        ResultMapping mapping = new ResultMapping.Builder(
                configuration, "count", "count", Long.class).build();
        return new ResultMap.Builder(configuration, statementId + "-Scalar",
                Long.class, Collections.singletonList(mapping)).build();
    }

    private static String placeholders(int count) {
        return placeholders(count, "ids");
    }

    private static String placeholders(int count, String paramName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("#{").append(paramName).append("[").append(i).append("]}");
        }
        return sb.toString();
    }
}