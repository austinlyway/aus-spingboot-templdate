package com.example.template.util;

import com.example.template.common.TypeReference;
import com.example.template.util.json.Fastjson2JsonAdapter;
import com.example.template.util.json.JacksonJsonAdapter;
import com.example.template.util.json.JsonAdapter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;

/**
 * Project-wide JSON facade, modelled after SLF4J.
 *
 * <p>Application classes should <strong>only</strong> call methods on this class to read
 * or write JSON. The concrete implementation is resolved once at startup from the
 * {@code json.implementation} property (e.g. {@code jackson}, {@code fastjson2}) and
 * may be swapped via {@link #setAdapter(JsonAdapter)} at any time — for example from
 * a feature flag, an Apollo config, or a test fixture.
 *
 * <h2>Choosing the right API</h2>
 *
 * <table>
 *   <tr><th>Need</th><th>Use</th></tr>
 *   <tr><td>Serialize</td><td>{@link #toJson(Object)}</td></tr>
 *   <tr><td>Deserialize a plain class</td>
 *       <td>{@link #fromJson(String, Class)}</td></tr>
 *   <tr><td>Deserialize a {@code List<MyClass>}</td>
 *       <td>{@link #toList(String, Class)} — no TypeReference boilerplate</td></tr>
 *   <tr><td>Deserialize {@code List<Map<String, MyClass>>} or other deeply generic types</td>
 *       <td>{@link #toList(String, TypeReference)} or {@link #fromJson(String, TypeReference)}</td></tr>
 * </table>
 */
public final class JsonHelper {

    private static final String DEFAULT_IMPLEMENTATION = "jackson";

    private static volatile JsonAdapter adapter;

    private JsonHelper() {
    }

    static {
        // Default binding picks Jackson to match the boot starter's auto-config.
        // Override via -Djson.implementation=fastjson2 or by calling setAdapter().
        String impl = System.getProperty("json.implementation", DEFAULT_IMPLEMENTATION);
        setAdapterByName(impl);
    }

    /**
     * Swap the underlying implementation at runtime. Useful for tests or hot config.
     */
    public static void setAdapter(JsonAdapter newAdapter) {
        if (newAdapter == null) {
            throw new IllegalArgumentException("JsonAdapter must not be null");
        }
        adapter = newAdapter;
    }

    /**
     * Convenience for selecting an implementation by short name (e.g. {@code "jackson"}).
     */
    public static void setAdapterByName(String name) {
        String key = name == null ? DEFAULT_IMPLEMENTATION : name.trim().toLowerCase(Locale.ROOT);
        switch (key) {
                case "fastjson2" -> setAdapter(new Fastjson2JsonAdapter());
                case "jackson", "" -> setAdapter(new JacksonJsonAdapter());
                default -> throw new IllegalArgumentException(
                        "Unknown JSON implementation: '" + name + "'. Supported: jackson, fastjson2.");
            }
    }

    /**
     * @return the active implementation's display name (e.g. {@code "jackson"}).
     */
    public static String activeImplementation() {
        return adapter().name();
    }

    /**
     * Serialize an object to a JSON string.
     */
    public static String toJson(Object obj) {
        return adapter().toJson(obj);
    }

    /**
     * Serialize to a pretty-printed JSON string.
     */
    public static String toPrettyJson(Object obj) {
        return adapter().toPrettyJson(obj);
    }

    /**
     * Deserialize a JSON string into the given class.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return adapter().fromJson(json, clazz);
    }

    /**
     * Deserialize a JSON string into a {@code List<T>} where {@code T} is the given
     * element class.
     *
     * <p>Convenience for the very common "JSON array → List of typed objects" case —
     * no need to write {@code new TypeReference<List<UserDto>>() {}}.
     *
     * @param json       the JSON string (must be a JSON array)
     * @param itemClass  the element class
     */
    public static <T> List<T> toList(String json, Class<T> itemClass) {
        return fromJson(json, listOf(itemClass));
    }

    /**
     * Deserialize a JSON array into a {@code List<T>} where {@code T} is itself a
     * generic type captured by a project-level {@link TypeReference}. Use this when
     * the element type is more complex than a single class — e.g.
     * {@code Map<String, UserDto>}.
     *
     * <p>Example:
     * <pre>
     * TypeReference&lt;Map&lt;String, UserDto&gt;&gt; elem = new TypeReference&lt;&gt;() {};
     * List&lt;Map&lt;String, UserDto&gt;&gt; rows = JsonHelper.toList(json, elem);
     * </pre>
     */
    public static <T> List<T> toList(String json, TypeReference<T> itemTypeReference) {
        if (itemTypeReference == null) {
            throw new IllegalArgumentException("TypeReference must not be null");
        }
        return fromJson(json, listOf(itemTypeReference.getType()));
    }

    /**
     * Deserialize a JSON string into a (possibly generic) type captured by a
     * project-level {@link TypeReference}.
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (typeReference == null) {
            throw new IllegalArgumentException("TypeReference must not be null");
        }
        return adapter().fromJson(json, typeReference);
    }

    /**
     * Deserialize a JSON string into an arbitrary {@link Type}. Provided for advanced uses;
     * prefer {@link #fromJson(String, TypeReference)} where possible.
     */
    public static <T> T fromJson(String json, Type type) {
        return adapter().fromJson(json, type);
    }

    // ------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------

    /**
     * Build a {@code List<itemClass>} {@link Type} suitable for passing to
     * {@link #fromJson(String, Type)}.
     */
    private static <T> Type listOf(Class<T> itemClass) {
        if (itemClass == null) {
            throw new IllegalArgumentException("itemClass must not be null");
        }
        return new ParameterizedTypeImpl(List.class, new Type[] {itemClass});
    }

    /**
     * Wrap any element {@link Type} (typically from {@link TypeReference#getType()})
     * into a {@code List<E>} {@link Type}.
     */
    private static Type listOf(Type elementType) {
        return new ParameterizedTypeImpl(List.class, new Type[] {elementType});
    }

    /**
     * Minimal {@link ParameterizedType} implementation — used to materialise
     * {@code List<elementType>} at runtime so we don't need anonymous subclasses
     * just to capture a generic.
     */
    private static final class ParameterizedTypeImpl implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;

        ParameterizedTypeImpl(Type rawType, Type[] actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType pt)) {
                return false;
            }
            return java.util.Objects.equals(rawType, pt.getRawType())
                    && java.util.Arrays.equals(actualTypeArguments, pt.getActualTypeArguments())
                    && java.util.Objects.equals(getOwnerType(), pt.getOwnerType());
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(actualTypeArguments)
                    ^ java.util.Objects.hashCode(rawType)
                    ^ java.util.Objects.hashCode(getOwnerType());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (rawType instanceof Class<?> c) {
                sb.append(c.getName());
            } else {
                sb.append(rawType);
            }
            sb.append('<');
            for (int i = 0; i < actualTypeArguments.length; i++) {
                if (i > 0) sb.append(", ");
                Type arg = actualTypeArguments[i];
                sb.append(arg instanceof Class<?> c ? c.getName() : arg);
            }
            sb.append('>');
            return sb.toString();
        }
    }

    private static JsonAdapter adapter() {
        JsonAdapter local = adapter;
        if (local == null) {
            // Defensive re-init if the static block was bypassed (e.g. some test runners).
            synchronized (JsonHelper.class) {
                if (adapter == null) {
                    setAdapterByName(DEFAULT_IMPLEMENTATION);
                    local = adapter;
                }
            }
        }
        return local;
    }
}