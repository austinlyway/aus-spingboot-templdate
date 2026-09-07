package com.example.template.mapper;

import com.example.template.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for the {@code user} table.
 */
@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    List<User> selectAll(@Param("limit") int limit);

    int insert(User user);

    int updateById(User user);

    int deleteById(@Param("id") Long id);
}