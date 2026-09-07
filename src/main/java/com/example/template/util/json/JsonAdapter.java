package com.example.template.util.json;

import com.example.template.common.TypeReference;

import java.lang.reflect.Type;

/**
 * Internal SPI used by {@link JsonHelper} to delegate to a concrete JSON library.
 *
 * <p>Like SLF4J's {@code Logger} interface, callers never see this — they only see
 * {@link JsonHelper}. Implementations convert the project-level {@link TypeReference}
 * into their own native type mechanism so the rest of the codebase stays library-agnostic.
 */
public interface JsonAdapter {

    /**
     * Serialize an object to a JSON string.
     */
    String toJson(Object obj);

    /**
     * Deserialize a JSON string into the given type.
     */
    <T> T fromJson(String json, Class<T> clazz);

    /**
     * Deserialize a JSON string into the (possibly generic) type captured by {@code typeReference}.
     */
    <T> T fromJson(String json, TypeReference<T> typeReference);

    /**
     * Deserialize a JSON string into the given raw {@link Type}. Provided for advanced uses
     * where the caller already has a {@link Type} but no {@code TypeReference}.
     */
    <T> T fromJson(String json, Type type);

    /**
     * Adapter identity (e.g. "jackson", "fastjson2") — useful for diagnostics.
     */
    String name();

    /**
     * Serialize an object to a pretty-printed JSON string. Default delegates to
     * {@link #toJson(Object)}; adapters may override for library-specific formatting.
     */
    default String toPrettyJson(Object obj) {
        return toJson(obj);
    }
}