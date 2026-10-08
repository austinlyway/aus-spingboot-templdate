package com.example.template.mybatis;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-table CRUD support that does <strong>not</strong> require a
 * {@code @Mapper} interface or any XML — you simply declare a Spring bean of
 * {@code SingleEntityMapper<User>} (or any other annotated entity) and the
 * generic type carries the table metadata.
 *
 * <p>This is the right choice when:
 * <ul>
 *   <li>You have a single entity with no mapper-specific queries — just CRUD.</li>
 *   <li>You want a quick, programmatic CRUD layer without committing to a
 *       {@code @Mapper} interface or writing XML.</li>
 *   <li>You want to keep the entity definition self-contained and avoid one
 *       Java file per table.</li>
 * </ul>
 *
 * <p>For entities that need entity-specific SQL (joins, sub-queries, custom
 * filters), prefer extending {@link BaseMapper}{@code <T>} instead — you get
 * the same CRUD methods for free plus the ability to add custom methods with
 * their own SQL bindings.
 *
 * <p>Example:
 * <pre>
 * &#64;Configuration
 * public class MapperConfig {
 *     &#64;Bean public SingleEntityMapper&lt;User&gt; userMapper(SqlSessionFactory f) {
 *         return new SingleEntityMapper&lt;&gt;(User.class, f);
 *     }
 * }
 *
 * &#64;Autowired SingleEntityMapper&lt;User&gt; userMapper;
 * userMapper.insert(user);
 * </pre>
 *
 * <p>Implementation: at the first call to each operation we build a
 * {@link MappedStatement} from the static SQL produced by
 * {@link SqlProvider} and register it on the MyBatis {@link Configuration}.
 * Subsequent calls execute the statement via the standard
 * {@link SqlSession} API.
 */
public class SingleEntityMapper<T> {

    private final Class<T> entityClass;
    private final SqlSessionFactory sqlSessionFactory;
    private final Configuration configuration;
    private final String namespace;
    private final ConcurrentHashMap<String, MappedStatement> statements = new ConcurrentHashMap<>();

    /**
     * Build a {@code SingleEntityMapper} for the given entity class. The
     * generic type parameter is not available at runtime, so callers pass the
     * entity class explicitly.
     */
    public SingleEntityMapper(Class<T> entityClass, SqlSessionFactory sqlSessionFactory) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        this.entityClass = entityClass;
        this.sqlSessionFactory = sqlSessionFactory;
        this.configuration = sqlSessionFactory.getConfiguration();
        this.namespace = "SingleEntityMapper$" + entityClass.getSimpleName();
    }

    // ------------------------------------------------------------------
    // insert / update / delete
    // ------------------------------------------------------------------

    /**
     * Insert a new row. Audit fields and the soft-delete flag are auto-populated.
     */
    public int insert(T entity) {
        AuditorProvider auditor = SqlProvider.auditor();
        SqlProvider.applyAuditFields(entity, true, auditor);
        // Pass the entity itself as the top-level parameter. The audit fields
        // are stamped on the entity (via applyAuditFields), and the SQL uses
        // the entity's own field names (#{createdAt}, #{createdBy}, etc.).
        // This way MyBatis can write the generated id back to entity.id.
        MappedStatement ms = statement("insert",
                SqlProvider.buildInsert(entityClass, false, true),
                SqlCommandType.INSERT, entity);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int rows = session.insert(ms.getId(), entity);
            session.commit();
            return rows;
        }
    }

    /**
     * Update an existing row by primary key. {@code @UpdatedAt} and
     * {@code @UpdatedBy} are auto-stamped; {@code @CreatedAt}/{@code @CreatedBy}
     * are preserved.
     */
    public int updateById(T entity) {
        AuditorProvider auditor = SqlProvider.auditor();
        SqlProvider.applyAuditFields(entity, false, auditor);
        MappedStatement ms = statement("updateById",
                SqlProvider.buildUpdateById(entityClass, false, true),
                SqlCommandType.UPDATE, entity);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int rows = session.update(ms.getId(), entity);
            session.commit();
            return rows;
        }
    }

    /**
     * Hard delete by primary key. Bypasses the soft-delete column.
     */
    public int hardDeleteById(Object id) {
        MappedStatement ms = statement("hardDeleteById",
                SqlProvider.buildDeleteById(entityClass, true),
                SqlCommandType.DELETE, id);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int rows = session.delete(ms.getId(), id);
            session.commit();
            return rows;
        }
    }

    /**
     * Delete by primary key. If the entity has a {@link LogicDelete} column,
     * the row is marked deleted (UPDATE); otherwise the row is removed (DELETE).
     */
    public int deleteById(Object id) {
        AuditorProvider auditor = SqlProvider.auditor();
        Map<String, Object> params = Map.of(
                "id", id,
                "audit_now", auditor.now(),
                "audit_user", auditor.currentUser());
        // For UPDATE, no result map is needed (returns int affected rows).
        MappedStatement ms = statement("deleteById",
                SqlProvider.buildDeleteById(entityClass, false),
                SqlCommandType.UPDATE, params, false);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int rows = session.update(ms.getId(), params);
            session.commit();
            return rows;
        }
    }

    // ------------------------------------------------------------------
    // select
    // ------------------------------------------------------------------

    /**
     * Look up a row by primary key. Soft-deleted rows are excluded.
     */
    public T selectById(Object id) {
        MappedStatement ms = statement("selectById", SqlProvider.buildSelectById(entityClass),
                SqlCommandType.SELECT, id);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) session.selectList(ms.getId(), id);
            if (rows.isEmpty()) {
                return null;
            }
            return (T) SqlProvider.mapToEntity(rows.get(0), entityClass);
        }
    }

    /**
     * Return every non-soft-deleted row.
     */
    public List<T> selectAll() {
        MappedStatement ms = statement("selectAll", SqlProvider.buildSelectAll(entityClass),
                SqlCommandType.SELECT, null);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return selectAllAndMap(session, ms, null);
        }
    }

    /**
     * Look up rows by a list of primary keys. Soft-deleted rows are excluded.
     */
    public List<T> selectByIds(List<? extends Object> ids) {
        // Use the "list" parameter name (the default MyBatis name for an
        // un-annotated List parameter); BaseMapper uses @Param("ids") and
        // calls the same builder with a different name.
        MappedStatement ms = statement("selectByIds",
                SqlProvider.buildSelectByIds(entityClass, ids == null ? 0 : ids.size(), "list"),
                SqlCommandType.SELECT, ids);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return selectAllAndMap(session, ms, ids);
        }
    }

    /**
     * Count non-soft-deleted rows.
     */
    public long count() {
        // Use a scalar Long result type so MyBatis returns a Long instead of
        // trying to map the COUNT(*) row into a LinkedHashMap of all columns.
        MappedStatement ms = statement("count", SqlProvider.buildCount(entityClass),
                SqlCommandType.SELECT, null, true);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Object n = session.selectOne(ms.getId(), Collections.emptyMap());
            return n == null ? 0L : ((Number) n).longValue();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<T> selectAllAndMap(SqlSession session, MappedStatement ms, Object parameter) {
        return (List<T>) SqlProvider.mapsToEntities(
                (List<Map<String, Object>>) (List<?>) session.selectList(ms.getId(), parameter),
                entityClass);
    }

    /**
     * Build a {@link MappedStatement} (cached) and register it in the MyBatis
     * {@link Configuration} so it can be invoked by id via the standard
     * {@code SqlSession.selectList/insert/update/delete} API.
     */
    private MappedStatement statement(String id, String sql,
                                     SqlCommandType commandType, Object sampleParam) {
        return statement(id, sql, commandType, sampleParam, false);
    }

    private MappedStatement statement(String id, String sql,
                                     SqlCommandType commandType, Object sampleParam,
                                     boolean scalarResult) {
        return statements.computeIfAbsent(id, k ->
                buildAndRegisterStatement(id, sql, commandType, sampleParam, scalarResult));
    }

    private MappedStatement buildAndRegisterStatement(String id, String sql,
                                                      SqlCommandType commandType, Object sampleParam) {
        return buildAndRegisterStatement(id, sql, commandType, sampleParam, false);
    }

    private MappedStatement buildAndRegisterStatement(String id, String sql,
                                                      SqlCommandType commandType, Object sampleParam,
                                                      boolean scalarResult) {
        String statementId = namespace + "." + id;
        // Use MyBatis's SqlSourceBuilder to parse #{...} placeholders into
        // proper ParameterMappings — this is the same pipeline that XML
        // mapper files go through, so placeholders get bound correctly.
        Class<?> parameterType = sampleParam == null ? Object.class : sampleParam.getClass();
        SqlSource sqlSource = new SqlSourceBuilder(configuration)
                .parse(sql, parameterType, Collections.<String, Object>emptyMap());

        MappedStatement.Builder builder = new MappedStatement.Builder(
                configuration, statementId, sqlSource, commandType);
        if (commandType == SqlCommandType.SELECT) {
            if (scalarResult) {
                builder.resultMaps(Collections.singletonList(
                        SqlProvider.buildScalarLongResultMap(configuration, statementId)));
            } else {
                builder.resultMaps(Collections.singletonList(buildMapResultMap(statementId)));
            }
        }
        if (commandType == SqlCommandType.INSERT) {
            // Auto-populate the entity's id field from the generated key.
            builder.keyGenerator(new org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator());
            builder.keyProperty("id");
            builder.keyColumn("id");
        }
        MappedStatement ms = builder.build();

        // Register in the MyBatis Configuration so SqlSession can look it up.
        configuration.addMappedStatement(ms);
        return ms;
    }

    /**
     * Build a result map that maps each column to a {@code LinkedHashMap}
     * entry. We do this instead of binding to the entity type because the
     * MappedStatement builder doesn't know about our generic T.
     */
    private ResultMap buildMapResultMap(String statementId) {
        List<ResultMapping> mappings = new ArrayList<>();
        EntityMeta meta = EntityMeta.of(entityClass);
        for (EntityField f : meta.getSelectableFields()) {
            List<ResultFlag> flags = new ArrayList<>();
            if (f.isId()) {
                flags.add(ResultFlag.ID);
            }
            // Constructor signature: (Configuration, property, column, javaType).
            // The first arg is the *property* on the result object (LinkedHashMap key);
            // the third arg is the database column name. We use the column name as
            // the property so the resulting map has snake_case keys matching the
            // SQL columns.
            ResultMapping mapping = new ResultMapping.Builder(
                    configuration, f.getColumnName(), f.getColumnName(), f.getType())
                    .flags(flags)
                    .build();
            mappings.add(mapping);
        }
        return new ResultMap.Builder(configuration, statementId + "-Inline",
                java.util.LinkedHashMap.class, mappings).build();
    }
}