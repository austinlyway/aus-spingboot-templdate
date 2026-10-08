package com.example.template.mybatis;

import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-facing bridge interface holding the {@code @XxxProvider}-annotated
 * methods that drive the actual SQL. {@link BaseMapper}'s default methods
 * call these (after pushing the entity type onto {@link SqlProvider#LAST_ENTITY_TYPE}),
 * letting us reuse one provider class for every entity type.
 *
 * <p>This interface is not intended to be implemented directly — it is wired
 * up automatically by MyBatis's {@code @MapperScan} / {@code @Mapper} machinery
 * via the mapper proxy.
 */
public interface MapperBridge {

    @InsertProvider(type = SqlProvider.class, method = "insert")
    @Options(useGeneratedKeys = true, keyProperty = "entity.id")
    int bridgeInsert(@Param("entity") Object entity,
                     @Param("audit_now") LocalDateTime auditNow,
                     @Param("audit_user") Object auditUser);

    @UpdateProvider(type = SqlProvider.class, method = "updateById")
    int bridgeUpdateById(@Param("entity") Object entity,
                         @Param("audit_now") LocalDateTime auditNow,
                         @Param("audit_user") Object auditUser);

    @DeleteProvider(type = SqlProvider.class, method = "deleteById")
    int bridgeDeleteById(@Param("id") Object id,
                         @Param("audit_now") LocalDateTime auditNow,
                         @Param("audit_user") Object auditUser);

    @DeleteProvider(type = SqlProvider.class, method = "hardDeleteById")
    int bridgeHardDeleteById(@Param("id") Object id);

    @SelectProvider(type = SqlProvider.class, method = "selectById")
    List<java.util.Map<String, Object>> bridgeSelectById(@Param("id") Object id);

    @SelectProvider(type = SqlProvider.class, method = "selectAll")
    List<java.util.Map<String, Object>> bridgeSelectAll();

    @SelectProvider(type = SqlProvider.class, method = "selectByIds")
    List<java.util.Map<String, Object>> bridgeSelectByIds(@Param("ids") List<?> ids);

    @SelectProvider(type = SqlProvider.class, method = "count")
    long bridgeCount();
}