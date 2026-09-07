package com.example.template.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * {@link FactoryBean} that produces a Retrofit-backed implementation of a
 * {@link RetrofitClient}-annotated interface.
 *
 * <p>Pass the service interface to the constructor; the factory bean will look up
 * the host, timeouts, and logging flag from the {@code clients.<clientName>.*} keys
 * (sourced from application.yml or Apollo) and produce a Retrofit-backed proxy.
 */
@Slf4j
public class RetrofitClientFactoryBean<T> implements FactoryBean<T> {

    private Environment environment;

    private final Class<T> serviceInterface;

    public RetrofitClientFactoryBean(Class<T> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    /**
     * Spring-injected {@link Environment} used to read {@code clients.<name>.*}.
     */
    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public T getObject() {
        RetrofitClient annotation = serviceInterface.getAnnotation(RetrofitClient.class);
        if (annotation == null) {
            throw new IllegalStateException(
                    serviceInterface.getName() + " must be annotated with @RetrofitClient");
        }
        String clientName = annotation.clientName();
        ClientProperties props = loadProperties(clientName);
        if (props.getHost() == null || props.getHost().isBlank()) {
            throw new IllegalStateException(
                    "Cannot build Retrofit client '" + clientName + "': no host configured under 'clients." + clientName + ".host'");
        }
        OkHttpClient okHttp = buildOkHttp(props);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(props.getHost())
                .client(okHttp)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(JsonHelperConverterFactory.create())
                .build();
        log.info("Built Retrofit client '{}' ({} -> {}, connect={}ms read={}ms write={}ms)",
                clientName, serviceInterface.getName(), props.getHost(),
                props.getConnectTimeoutMs(), props.getReadTimeoutMs(), props.getWriteTimeoutMs());
        return retrofit.create(serviceInterface);
    }

    @Override
    public Class<?> getObjectType() {
        return serviceInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private ClientProperties loadProperties(String clientName) {
        ClientProperties props = new ClientProperties();
        String prefix = "clients." + clientName + ".";
        props.setHost(environment.getProperty(prefix + "host"));
        props.setConnectTimeoutMs(parseLong(environment.getProperty(prefix + "connect-timeout-ms"), props.getConnectTimeoutMs()));
        props.setReadTimeoutMs(parseLong(environment.getProperty(prefix + "read-timeout-ms"), props.getReadTimeoutMs()));
        props.setWriteTimeoutMs(parseLong(environment.getProperty(prefix + "write-timeout-ms"), props.getWriteTimeoutMs()));
        props.setEnableLogging(parseBoolean(environment.getProperty(prefix + "enable-logging"), props.isEnableLogging()));
        return props;
    }

    private OkHttpClient buildOkHttp(ClientProperties props) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(props.getWriteTimeoutMs(), TimeUnit.MILLISECONDS);
        if (props.isEnableLogging()) {
            builder.addInterceptor(new HttpLoggingInterceptor(log::debug)
                    .setLevel(HttpLoggingInterceptor.Level.BODY));
        }
        return builder.build();
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBoolean(String s, boolean fallback) {
        if (s == null || s.isBlank()) return fallback;
        return Boolean.parseBoolean(s.trim());
    }
}