package com.example.template.mybatis;

import java.lang.reflect.Field;

/**
 * Cached per-field metadata derived from reflection. Built once per entity class and
 * reused for every CRUD call.
 */
public final class EntityField {

    private final Field field;
    private final String columnName;
    private final boolean isId;
    private final boolean isLogicDelete;
    private final boolean isCreatedAt;
    private final boolean isUpdatedAt;
    private final boolean isCreatedBy;
    private final boolean isUpdatedBy;
    private final boolean isIgnored;
    private final int logicDeleteActiveValue;
    private final int logicDeleteDeletedValue;

    EntityField(Field field,
                String columnName,
                boolean isId,
                boolean isLogicDelete,
                int logicDeleteActiveValue,
                int logicDeleteDeletedValue,
                boolean isCreatedAt,
                boolean isUpdatedAt,
                boolean isCreatedBy,
                boolean isUpdatedBy,
                boolean isIgnored) {
        this.field = field;
        this.columnName = columnName;
        this.isId = isId;
        this.isLogicDelete = isLogicDelete;
        this.logicDeleteActiveValue = logicDeleteActiveValue;
        this.logicDeleteDeletedValue = logicDeleteDeletedValue;
        this.isCreatedAt = isCreatedAt;
        this.isUpdatedAt = isUpdatedAt;
        this.isCreatedBy = isCreatedBy;
        this.isUpdatedBy = isUpdatedBy;
        this.isIgnored = isIgnored;
        this.field.setAccessible(true);
    }

    public Field getField() {
        return field;
    }

    public String getName() {
        return field.getName();
    }

    public String getColumnName() {
        return columnName;
    }

    public Class<?> getType() {
        return field.getType();
    }

    public boolean isId() {
        return isId;
    }

    public boolean isLogicDelete() {
        return isLogicDelete;
    }

    public boolean isCreatedAt() {
        return isCreatedAt;
    }

    public boolean isUpdatedAt() {
        return isUpdatedAt;
    }

    public boolean isCreatedBy() {
        return isCreatedBy;
    }

    public boolean isUpdatedBy() {
        return isUpdatedBy;
    }

    public boolean isIgnored() {
        return isIgnored;
    }

    public int getLogicDeleteActiveValue() {
        return logicDeleteActiveValue;
    }

    public int getLogicDeleteDeletedValue() {
        return logicDeleteDeletedValue;
    }

    public Object getValue(Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot read field " + field + " on " + target.getClass().getName(), e);
        }
    }

    public void setValue(Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot write field " + field + " on " + target.getClass().getName(), e);
        }
    }
}