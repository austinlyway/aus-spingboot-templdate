package com.example.template.client;

import com.example.template.util.JsonHelper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Retrofit {@link Converter.Factory} backed by the project's {@link JsonHelper}.
 *
 * <p>This keeps body serialization consistent with the rest of the codebase — any swap
 * of the underlying JSON library in {@link JsonHelper} applies automatically here too.
 */
@Slf4j
public class JsonHelperConverterFactory extends Converter.Factory {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    public static JsonHelperConverterFactory create() {
        return new JsonHelperConverterFactory();
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type,
                                                          Annotation[] parameterAnnotations,
                                                          Annotation[] methodAnnotations,
                                                          Retrofit retrofit) {
        return (Converter<Object, RequestBody>) value -> RequestBody.create(JsonHelper.toJson(value), JSON_MEDIA);
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                            Annotation[] annotations,
                                                            Retrofit retrofit) {
        return value -> {
            try {
                return JsonHelper.fromJson(value.string(), type);
            } catch (Exception e) {
                log.error("Failed to deserialize response body to type {}", type, e);
                throw e;
            }
        };
    }
}