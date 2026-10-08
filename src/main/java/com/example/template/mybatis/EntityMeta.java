package com.example.template.mybatis;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached per-class metadata used by {@link BaseMapper}. One instance per entity
 * type, stored in a static {@link ConcurrentHashMap} for fast lookup.
 */
public final class EntityMeta {

    private static final Map<Class<?>, EntityMeta> CACHE = new ConcurrentHashMap<>();

    private final Class<?> entityClass;
    private final String tableName;
    private final EntityField idField;
    private final EntityField logicDeleteField;
    private final List<EntityField> insertableFields;     // all non-id, non-ignored fields
    private final List<EntityField> updatableFields;      // all non-id, non-ignored, non-CreatedAt/CreatedBy fields
    private final List<EntityField> selectableFields;     // all non-ignored fields
    private final Map<String, EntityField> byColumn;       // column name → field

    private EntityMeta(Class<?> entityClass,
                       String tableName,
                       EntityField idField,
                       EntityField logicDeleteField,
                       List<EntityField> insertableFields,
                       List<EntityField> updatableFields,
                       List<EntityField> selectableFields) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.idField = idField;
        this.logicDeleteField = logicDeleteField;
        this.insertableFields = insertableFields;
        this.updatableFields = updatableFields;
        this.selectableFields = selectableFields;
        Map<String, EntityField> byColumn = new LinkedHashMap<>();
        for (EntityField f : selectableFields) {
            byColumn.put(f.getColumnName().toLowerCase(), f);
        }
        this.byColumn = Collections.unmodifiableMap(byColumn);
    }

    public static EntityMeta of(Class<?> entityClass) {
        return CACHE.computeIfAbsent(entityClass, EntityMeta::build);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static EntityMeta build(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException(
                    entityClass.getName() + " must be annotated with @Table");
        }

        EntityField id = null;
        EntityField logicDelete = null;
        List<EntityField> insertable = new ArrayList<>();
        List<EntityField> updatable = new ArrayList<>();
        List<EntityField> selectable = new ArrayList<>();

        // Walk up the class hierarchy so inherited fields (e.g. an abstract base
        // entity with @Id) are included.
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                EntityField ef = parseField(field);
                if (ef.isIgnored()) {
                    continue;
                }
                selectable.add(ef);
                if (ef.isId()) {
                    id = ef;
                    // Primary keys are not part of INSERT column lists by default.
                    continue;
                }
                if (ef.isLogicDelete()) {
                    logicDelete = ef;
                }
                insertable.add(ef);

                // @CreatedAt / @CreatedBy should not be writable by application code
                // via updateById, but @UpdatedAt / @UpdatedBy should.
                if (!ef.isCreatedAt() && !ef.isCreatedBy()) {
                    updatable.add(ef);
                }
            }
            current = current.getSuperclass();
        }

        if (id == null) {
            throw new IllegalArgumentException(
                    entityClass.getName() + " has no field annotated with @Id");
        }
        return new EntityMeta(entityClass, table.value(), id, logicDelete,
                List.copyOf(insertable), List.copyOf(updatable), List.copyOf(selectable));
    }

    private static EntityField parseField(Field field) {
        Column column = field.getAnnotation(Column.class);
        Id id = field.getAnnotation(Id.class);
        LogicDelete logicDelete = field.getAnnotation(LogicDelete.class);
        CreatedAt createdAt = field.getAnnotation(CreatedAt.class);
        UpdatedAt updatedAt = field.getAnnotation(UpdatedAt.class);
        CreatedBy createdBy = field.getAnnotation(CreatedBy.class);
        UpdatedBy updatedBy = field.getAnnotation(UpdatedBy.class);
        Ignore ignore = field.getAnnotation(Ignore.class);

        String columnName = column != null ? column.value() : camelToSnake(field.getName());

        return new EntityField(
                field,
                columnName,
                id != null,
                logicDelete != null,
                logicDelete != null ? logicDelete.notDeletedValue() : 0,
                logicDelete != null ? logicDelete.deletedValue() : 1,
                createdAt != null,
                updatedAt != null,
                createdBy != null,
                updatedBy != null,
                ignore != null);
    }

    /** Convert {@code createdAt} → {@code created_at}. */
    static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public EntityField getIdField() {
        return idField;
    }

    public EntityField getLogicDeleteField() {
        return logicDeleteField;
    }

    public boolean hasLogicDelete() {
        return logicDeleteField != null;
    }

    public List<EntityField> getInsertableFields() {
        return insertableFields;
    }

    public List<EntityField> getUpdatableFields() {
        return updatableFields;
    }

    public List<EntityField> getSelectableFields() {
        return selectableFields;
    }

    public Map<String, EntityField> getByColumn() {
        return byColumn;
    }
}