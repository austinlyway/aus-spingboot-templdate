package com.example.template.service;

import com.example.template.dto.UserDto;

import java.util.List;

/**
 * Service contract for user operations. Implemented by {@code UserServiceImpl}.
 */
public interface UserService {

    UserDto getById(Long id);

    List<UserDto> listRecent(int limit);

    UserDto create(UserDto dto);

    UserDto update(UserDto dto);

    boolean delete(Long id);
}