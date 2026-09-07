package com.example.template.util.json;

import com.alibaba.fastjson2.JSON;
import com.example.template.common.TypeReference;

import java.lang.reflect.Type;

/**
 * Fastjson2-backed {@link JsonAdapter}.
 *
 * <p>This is the only place the codebase references fastjson2 directly,
 * keeping the rest of the application library-agnostic (similar to how SLF4J
 * hides Log4j/Logback).
 */
public class Fastjson2JsonAdapter implements JsonAdapter {

    @Override
    public String toJson(Object obj) {
        try {
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            throw new JsonException("Fastjson2 serialize failed", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            throw new JsonException("Fastjson2 deserialize failed for " + clazz.getName(), e);
        }
    }

    @Override
    public <T> T fromJson(String json, TypeReference<T> typeReference) {
        return fromJson(json, typeReference.getType());
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return JSON.parseObject(json, type);
        } catch (Exception e) {
            throw new JsonException("Fastjson2 deserialize failed for type " + type, e);
        }
    }

    @Override
    public String name() {
        return "fastjson2";
    }

    @Override
    public String toPrettyJson(Object obj) {
        try {
            return JSON.toJSONString(obj, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
        } catch (Exception e) {
            throw new JsonException("Fastjson2 pretty serialize failed", e);
        }
    }
}