package com.example.template.controller;

import com.example.template.config.MyBatisConfig;
import com.example.template.dto.UserDto;
import com.example.template.exception.NotFoundException;
import com.example.template.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice test for {@link UserController}. Verifies HTTP contracts (status codes,
 * JSON shape) without spinning up the full application context.
 *
 * <p>JSON serialization flows through the real {@link com.example.template.util.JsonHelper},
 * so we are also verifying the JSON facade works end-to-end in a Spring context.
 */
@WebMvcTest(controllers = UserController.class,
        excludeAutoConfiguration = {DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {MyBatisConfig.class}))
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /users/{id} returns 200 and the user body")
    void getByIdOk() throws Exception {
        when(userService.getById(1L)).thenReturn(
                UserDto.builder().id(1L).username("alice").email("a@x.com").age(20).build()
        );

        mockMvc.perform(get("/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    @DisplayName("GET /users/{id} returns 404 when the service throws NotFoundException")
    void getByIdNotFound() throws Exception {
        when(userService.getById(99L)).thenThrow(new NotFoundException("missing"));

        mockMvc.perform(get("/users/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("GET /users returns a list of users")
    void list() throws Exception {
        when(userService.listRecent(20)).thenReturn(List.of(
                UserDto.builder().id(1L).username("alice").build(),
                UserDto.builder().id(2L).username("bob").build()
        ));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("POST /users accepts JSON and returns the created entity")
    void create() throws Exception {
        when(userService.create(any(UserDto.class))).thenAnswer(inv -> {
            UserDto in = inv.getArgument(0);
            return UserDto.builder().id(7L).username(in.getUsername()).email(in.getEmail()).build();
        });

        String body = "{\"username\":\"alice\",\"email\":\"a@x.com\",\"age\":20}";

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    @DisplayName("PUT /users/{id} updates the entity")
    void update() throws Exception {
        when(userService.update(any(UserDto.class))).thenAnswer(inv -> {
            UserDto in = inv.getArgument(0);
            return UserDto.builder().id(in.getId()).username(in.getUsername()).build();
        });

        String body = "{\"username\":\"alice2\"}";

        mockMvc.perform(put("/users/{id}", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.username").value("alice2"));
    }

    @Test
    @DisplayName("DELETE /users/{id} returns 200 with success flag")
    void deleteUser() throws Exception {
        when(userService.delete(eq(3L))).thenReturn(true);

        mockMvc.perform(delete("/users/{id}", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}