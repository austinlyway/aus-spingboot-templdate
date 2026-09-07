package com.example.template.util.json;

import com.example.template.common.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.lang.reflect.Type;

/**
 * Jackson-backed {@link JsonAdapter}.
 *
 * <p>This is the only place the codebase references Jackson types directly,
 * keeping the rest of the application library-agnostic (similar to how SLF4J
 * hides Log4j/Logback).
 */
public class JacksonJsonAdapter implements JsonAdapter {

    private final ObjectMapper objectMapper;

    public JacksonJsonAdapter() {
        this(new ObjectMapper());
    }

    public JacksonJsonAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Sensible defaults for an HTTP-facing service.
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new JsonException("Jackson serialize failed", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new JsonException("Jackson deserialize failed for " + clazz.getName(), e);
        }
    }

    @Override
    public <T> T fromJson(String json, TypeReference<T> typeReference) {
        return fromJson(json, typeReference.getType());
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            // Jackson wants a JavaType. Convert the raw Type into one.
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructType(type));
        } catch (Exception e) {
            throw new JsonException("Jackson deserialize failed for type " + type, e);
        }
    }

    @Override
    public String name() {
        return "jackson";
    }

    @Override
    public String toPrettyJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new JsonException("Jackson pretty serialize failed", e);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}