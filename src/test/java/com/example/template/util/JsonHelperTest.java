package com.example.template.util;

import com.example.template.common.TypeReference;
import com.example.template.dto.UserDto;
import com.example.template.util.json.Fastjson2JsonAdapter;
import com.example.template.util.json.JacksonJsonAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the JSON facade — verifies that the implementation can be swapped at
 * runtime and that both Jackson and fastjson2 adapters behave identically for our
 * primary use cases.
 */
class JsonHelperTest {

    @AfterEach
    void resetDefault() {
        JsonHelper.setAdapter(new JacksonJsonAdapter());
    }

    @Test
    @DisplayName("Default implementation is Jackson")
    void defaultIsJackson() {
        assertEquals("jackson", JsonHelper.activeImplementation());
    }

    @Test
    @DisplayName("Simple object round-trip via Jackson")
    void simpleRoundTripJackson() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "alice");
        input.put("age", 30);

        String json = JsonHelper.toJson(input);
        assertNotNull(json);

        Map<String, Object> out = JsonHelper.fromJson(json, Map.class);
        assertEquals("alice", out.get("name"));
        assertEquals(30, out.get("age"));
    }

    @Test
    @DisplayName("Simple object round-trip via fastjson2")
    void simpleRoundTripFastjson2() {
        JsonHelper.setAdapter(new Fastjson2JsonAdapter());

        Map<String, Object> input = new HashMap<>();
        input.put("name", "bob");
        input.put("age", 42);

        String json = JsonHelper.toJson(input);
        Map<String, Object> out = JsonHelper.fromJson(json, Map.class);

        assertEquals("bob", out.get("name"));
        assertEquals(42, out.get("age"));
        assertEquals("fastjson2", JsonHelper.activeImplementation());
    }

    @Test
    @DisplayName("Multi-level generic via TypeReference (Jackson)")
    void multiLevelGenericJackson() {
        String json = "[{\"name\":\"alice\",\"age\":30},{\"name\":\"bob\",\"age\":25}]";

        TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<>() {};

        List<Map<String, Object>> result = JsonHelper.fromJson(json, typeRef);
        assertEquals(2, result.size());
        assertEquals("alice", result.get(0).get("name"));
        assertEquals(25, result.get(1).get("age"));
    }

    @Test
    @DisplayName("Multi-level generic via TypeReference (fastjson2)")
    void multiLevelGenericFastjson2() {
        JsonHelper.setAdapter(new Fastjson2JsonAdapter());

        String json = "[{\"name\":\"alice\",\"age\":30},{\"name\":\"bob\",\"age\":25}]";
        TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<>() {};

        List<Map<String, Object>> result = JsonHelper.fromJson(json, typeRef);
        assertEquals(2, result.size());
        assertTrue(result.get(0).containsKey("name"));
    }

    @Test
    @DisplayName("setAdapterByName rejects unknown implementations")
    void unknownImplementationRejected() {
        try {
            JsonHelper.setAdapterByName("bogus");
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown JSON implementation"));
        }
    }

    // ------------------------------------------------------------
    // toList convenience
    // ------------------------------------------------------------

    @Test
    @DisplayName("toList(String, Class) returns List<T> without TypeReference boilerplate (Jackson)")
    void toListWithClassJackson() {
        String json = "[{\"id\":1,\"username\":\"alice\"},{\"id\":2,\"username\":\"bob\"}]";
        List<UserDto> users = JsonHelper.toList(json, UserDto.class);
        assertEquals(2, users.size());
        assertEquals("alice", users.get(0).getUsername());
        assertEquals(2L, users.get(1).getId());
    }

    @Test
    @DisplayName("toList(String, Class) returns List<T> without TypeReference boilerplate (fastjson2)")
    void toListWithClassFastjson2() {
        JsonHelper.setAdapter(new Fastjson2JsonAdapter());

        String json = "[{\"id\":1,\"username\":\"alice\"},{\"id\":2,\"username\":\"bob\"}]";
        List<UserDto> users = JsonHelper.toList(json, UserDto.class);
        assertEquals(2, users.size());
        assertEquals("alice", users.get(0).getUsername());
        assertEquals(2L, users.get(1).getId());
    }

    @Test
    @DisplayName("toList handles an empty JSON array")
    void toListEmptyArray() {
        List<UserDto> users = JsonHelper.toList("[]", UserDto.class);
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    @DisplayName("toList(String, TypeReference) handles nested generic elements")
    void toListWithComplexElementType() {
        String json = "[{\"u\":{\"id\":1,\"username\":\"alice\"}},{\"u\":{\"id\":2,\"username\":\"bob\"}}]";

        // Each element is a Map<String, UserDto> — exercise the TypeReference overload.
        TypeReference<Map<String, UserDto>> elementRef = new TypeReference<>() {};
        List<Map<String, UserDto>> rows = JsonHelper.toList(json, elementRef);

        assertEquals(2, rows.size());
        UserDto alice = rows.get(0).get("u");
        assertNotNull(alice);
        assertEquals("alice", alice.getUsername());
        assertEquals(2L, rows.get(1).get("u").getId());
    }

    @Test
    @DisplayName("toList(String, Class) rejects null itemClass")
    void toListRejectsNullClass() {
        assertThrows(IllegalArgumentException.class, () -> JsonHelper.toList("[]", (Class<?>) null));
    }

    @Test
    @DisplayName("toList(String, TypeReference) rejects null typeReference")
    void toListRejectsNullTypeReference() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonHelper.toList("[]", (TypeReference<?>) null));
    }

    @Test
    @DisplayName("Round-trip: serialize List<T> with toJson then read back via toList")
    void roundTripListViaToJson() {
        List<UserDto> input = List.of(
                UserDto.builder().id(1L).username("alice").email("a@x.com").build(),
                UserDto.builder().id(2L).username("bob").email("b@x.com").build()
        );

        String json = JsonHelper.toJson(input);
        List<UserDto> output = JsonHelper.toList(json, UserDto.class);

        assertEquals(2, output.size());
        assertEquals("alice", output.get(0).getUsername());
        assertEquals("b@x.com", output.get(1).getEmail());
    }

    @Test
    @DisplayName("Element type is preserved at runtime (Jackson)")
    void toListPreservesElementType() {
        String json = "[{\"id\":1,\"username\":\"alice\"}]";
        List<UserDto> users = JsonHelper.toList(json, UserDto.class);
        assertEquals(1, users.size());
        // Crucially, the element is a real UserDto instance, not a LinkedHashMap.
        assertInstanceOf(UserDto.class, users.get(0));
    }
}