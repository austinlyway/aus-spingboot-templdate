package com.example.template.common;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Project-level TypeReference.
 *
 * <p>Allows callers to capture multi-level generic types (e.g. {@code List<UserDto>})
 * at compile time without directly depending on a specific JSON library.
 * Concrete implementations of {@link JsonHelper} translate this into the
 * underlying library's type reference mechanism (Jackson {@code TypeReference},
 * fastjson2 {@code TypeReference}, etc.).
 */
public abstract class TypeReference<T> {

    private final Type type;

    protected TypeReference() {
        Type superClass = getClass().getGenericSuperclass();
        if (!(superClass instanceof ParameterizedType parameterized)) {
            throw new IllegalArgumentException(
                    "TypeReference must be created with a concrete generic type, e.g. new TypeReference<List<UserDto>>() {}");
        }
        this.type = parameterized.getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }

    public Type getRawType() {
        return type instanceof ParameterizedType pt ? pt.getRawType() : type;
    }
}