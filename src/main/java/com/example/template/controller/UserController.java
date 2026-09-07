package com.example.template.controller;

import com.example.template.dto.ApiResponse;
import com.example.template.dto.UserDto;
import com.example.template.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for {@code User} operations.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ApiResponse<UserDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<UserDto>> list(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(userService.listRecent(limit));
    }

    @PostMapping
    public ApiResponse<UserDto> create(@RequestBody UserDto dto) {
        return ApiResponse.ok(userService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDto> update(@PathVariable Long id, @RequestBody UserDto dto) {
        dto.setId(id);
        return ApiResponse.ok(userService.update(dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.ok(userService.delete(id));
    }
}