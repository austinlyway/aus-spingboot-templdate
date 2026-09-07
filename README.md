# Spring Boot 3 Backend Template

A clean, opinionated Spring Boot 3 backend template that isolates the JSON library
behind a single facade, centralises file I/O, and routes every external HTTP call
through Retrofit + Apollo-driven configuration.

## Highlights

- **Spring Boot 3.2** on Java 17.
- **MyBatis** for persistence (XML mapper + interface) with H2 in-memory DB for tests.
- **Retrofit + OkHttp** for external HTTP services. Hosts are resolved from the
  `clients.<name>.host` key, expected to be supplied by **Apollo** at runtime.
- **SLF4J-style JSON facade** — application code uses `JsonHelper`, never
  `ObjectMapper` / `JSON` directly. Both `jackson` and `fastjson2` adapters ship
  in this template; swap via the `json.implementation` config key.
- **Project-level `TypeReference`** — generic types (e.g. `List<UserDto>`) are
  captured with `new TypeReference<>() {}` and translated to the underlying
  library's native type reference inside each adapter.
- **`FileHelper`** — every read / write / create of project files goes through
  one utility class.
- **JUnit 5 + Mockito + MockWebServer** for unit, slice, and integration tests.

## Package Layout

```
src/main/java/com/example/template/
├── Application.java               # @SpringBootApplication entry point
├── client/                        # External HTTP calls
│   ├── RetrofitClient.java        # @interface marker
│   ├── ClientHostResolver.java    # reads clients.<name>.host from Apollo/Spring
│   ├── RetrofitClientFactory.java # builds Retrofit + OkHttp clients
│   ├── OkHttpHelper.java          # ad-hoc OkHttp wrapper
│   ├── JsonHelperConverterFactory.java # Retrofit JSON converter (uses JsonHelper)
│   ├── ClientProperties.java      # per-client config model
│   ├── ClientPropertiesBinding.java
│   └── DemoClient.java            # sample @RetrofitClient interface
├── common/
│   └── TypeReference.java         # project-level generic type capture
├── config/
│   ├── JsonConfig.java            # binds json.implementation -> JsonHelper
│   ├── MyBatisConfig.java
│   └── ApolloConfig.java          # documentation of app.apollo.* keys
├── controller/
│   ├── UserController.java
│   └── DemoController.java
├── dto/
│   ├── ApiResponse.java
│   └── UserDto.java
├── entity/
│   └── User.java
├── exception/
│   ├── NotFoundException.java
│   └── GlobalExceptionHandler.java
├── mapper/
│   └── UserMapper.java
├── service/
│   ├── UserService.java
│   └── impl/UserServiceImpl.java
└── util/
    ├── JsonHelper.java            # SLF4J-style JSON facade
    ├── FileHelper.java            # all file I/O
    └── json/
        ├── JsonAdapter.java       # internal SPI
        ├── JacksonJsonAdapter.java
        ├── Fastjson2JsonAdapter.java
        └── JsonException.java
```

## JSON Usage

The rest of the codebase **must not** import `com.fasterxml.jackson.*` or
`com.alibaba.fastjson2.*` directly. Always go through `JsonHelper`:

```java
// plain class
String json = JsonHelper.toJson(user);
UserDto user = JsonHelper.fromJson(json, UserDto.class);

// JSON array → List<T> (most common case — no TypeReference needed)
List<UserDto> users = JsonHelper.toList(json, UserDto.class);

// deeply generic element type (e.g. List<Map<String, UserDto>>)
TypeReference<Map<String, UserDto>> elem = new TypeReference<>() {};
List<Map<String, UserDto>> rows = JsonHelper.toList(json, elem);
```

`toList(String, Class<T>)` is the recommended shortcut for the common
"JSON array → typed list" case. Reach for `TypeReference` only when the element
type is itself generic (nested maps, parameterized DTOs, etc.).

### Switching implementations

The active adapter is selected by `json.implementation` in `application.yml`:

```yaml
json:
  implementation: jackson   # or fastjson2
```

You can also swap at runtime (handy in tests or feature flags):

```java
JsonHelper.setAdapter(new Fastjson2JsonAdapter());
```

### Adding a new implementation

1. Implement `util/json/JsonAdapter.java`.
2. Register the name in `JsonHelper.setAdapterByName(...)`.

## External HTTP Calls

1. Declare an interface in the `client` package and annotate it with
   `@RetrofitClient(clientName = "my-service")`.
2. Add `clients.my-service.host=http://my-service:8080` in Apollo
   (or in `application.yml` for local dev).
3. Resolve the client via `RetrofitClientFactory.create(MyClient.class, "my-service")`.

```java
@RetrofitClient(clientName = "demo-client")
public interface DemoClient {
    @GET("/api/health")
    Call<String> health();
}
```

```java
DemoClient client = retrofitClientFactory.create(DemoClient.class, "demo-client");
Response<String> r = client.health().execute();
```

For one-off requests, use `OkHttpHelper` directly — both paths share the same
host resolution and JSON facade.

## File I/O

All file operations go through `FileHelper`:

```java
FileHelper.writeText("config/app.yml", yaml);
String yaml = FileHelper.readText("config/app.yml");
List<String> lines = FileHelper.readLines("config/app.yml");
```

`FileHelper` wraps `IOException` as an unchecked `FileHelperException`.

## Running

```bash
# build
mvn clean package

# run with the dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# tests
mvn test
```

## Apollo

This template assumes Apollo is the source of truth for `clients.*.host` and any
`app.*` settings. Apollo's Spring Boot starter is **not** bundled to keep the
dependency surface minimal — add `apollo-spring-boot-starter` and
`@EnableApolloConfig` on a config class to wire it in. The `ClientHostResolver`
already reads through Spring's `Environment`, so Apollo overrides flow
transparently.