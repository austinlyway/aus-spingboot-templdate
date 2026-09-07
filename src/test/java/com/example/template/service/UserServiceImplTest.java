package com.example.template.service;

import com.example.template.dto.UserDto;
import com.example.template.entity.User;
import com.example.template.exception.NotFoundException;
import com.example.template.mapper.UserMapper;
import com.example.template.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserServiceImpl} — uses Mockito for the MyBatis mapper
 * to keep the test scope narrow.
 */
class UserServiceImplTest {

    private UserMapper userMapper;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        service = new UserServiceImpl(userMapper);
    }

    @Test
    @DisplayName("getById returns a DTO when entity exists")
    void getByIdFound() {
        User user = User.builder().id(1L).username("alice").email("a@x.com").age(20).build();
        when(userMapper.selectById(1L)).thenReturn(user);

        UserDto dto = service.getById(1L);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("alice", dto.getUsername());
    }

    @Test
    @DisplayName("getById throws NotFoundException when entity is missing")
    void getByIdNotFound() {
        when(userMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.getById(99L));
    }

    @Test
    @DisplayName("listRecent clamps to at least 1 and maps entities to DTOs")
    void listRecent() {
        when(userMapper.selectAll(anyInt())).thenReturn(Arrays.asList(
                User.builder().id(1L).username("a").build(),
                User.builder().id(2L).username("b").build()
        ));

        List<UserDto> out = service.listRecent(0);

        assertEquals(2, out.size());
        assertEquals("a", out.get(0).getUsername());
        verify(userMapper).selectAll(eq(1)); // clamped to 1
    }

    @Test
    @DisplayName("create inserts then re-reads the entity")
    void create() {
        UserDto in = UserDto.builder().username("alice").email("a@x.com").age(20).build();

        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return 1;
        });
        when(userMapper.selectById(7L)).thenReturn(User.builder()
                .id(7L).username("alice").email("a@x.com").age(20).build());

        UserDto out = service.create(in);

        assertEquals(7L, out.getId());
        assertEquals("alice", out.getUsername());
    }

    @Test
    @DisplayName("delete returns true only when a row was removed")
    void delete() {
        when(userMapper.deleteById(1L)).thenReturn(1);
        when(userMapper.deleteById(2L)).thenReturn(0);

        assertTrue(service.delete(1L));
        assertFalse(service.delete(2L));
    }
}