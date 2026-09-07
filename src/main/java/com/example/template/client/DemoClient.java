package com.example.template.client;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Sample client interface. Every external HTTP service your project calls should be
 * declared as a Retrofit interface in the {@code client} package and annotated with
 * {@link RetrofitClient}.
 *
 * <p>Methods may return {@link Call} (callers do {@code .execute()} or {@code .enqueue()})
 * or {@code retrofit2.Response}. This example uses {@code Call} for clarity.
 */
@RetrofitClient(clientName = "demo-client")
public interface DemoClient {

    @GET("/api/health")
    Call<String> health();

    @GET("/api/users/{id}")
    Call<String> getUser(@retrofit2.http.Path("id") long id);

    @GET("/api/users")
    Call<String> listUsers(@Query("page") int page, @Query("size") int size);
}