package com.example.template.service.impl;

import com.example.template.dto.UserDto;
import com.example.template.entity.User;
import com.example.template.exception.NotFoundException;
import com.example.template.mapper.UserMapper;
import com.example.template.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserDto getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new NotFoundException("User not found: id=" + id);
        }
        return toDto(user);
    }

    @Override
    public List<UserDto> listRecent(int limit) {
        return userMapper.selectAll(Math.max(1, limit)).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserDto create(UserDto dto) {
        User entity = new User();
        BeanUtils.copyProperties(dto, entity, "id", "createdAt", "updatedAt");
        userMapper.insert(entity);
        log.info("Created user id={}", entity.getId());
        return getById(entity.getId());
    }

    @Override
    @Transactional
    public UserDto update(UserDto dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("id is required for update");
        }
        User entity = new User();
        BeanUtils.copyProperties(dto, entity, "id", "createdAt", "updatedAt");
        int rows = userMapper.updateById(entity);
        if (rows == 0) {
            throw new NotFoundException("User not found: id=" + dto.getId());
        }
        return getById(dto.getId());
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }
}