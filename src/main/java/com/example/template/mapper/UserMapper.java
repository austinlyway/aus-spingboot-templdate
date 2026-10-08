package com.example.template.mapper;

import com.example.template.entity.User;
import com.example.template.mybatis.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for the {@code users} table. All single-table CRUD operations
 * are inherited from {@link BaseMapper}.
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * Custom query — not covered by the generic BaseMapper. Demonstrates that
     * entity-specific SQL can coexist with the auto-generated CRUD.
     */
    List<User> findByEmailLike(@Param("pattern") String pattern);
}