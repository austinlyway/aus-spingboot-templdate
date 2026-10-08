# Spring Boot 3 Backend Template

A clean, opinionated Spring Boot 3 backend template that isolates the JSON library
behind a single facade, centralises file I/O, and routes every external HTTP call
through Retrofit + Apollo-driven configuration. Ships with a MyBatis helper that
auto-generates single-table CRUD from entity annotations.

## Highlights

- **Spring Boot 3.2** on Java 17.
- **MyBatis** for persistence with H2 in-memory DB for tests.
- **`BaseMapper<T>`** — annotation-driven single-table CRUD; `selectById`,
  `selectAll`, `insert`, `updateById`, `deleteById`, `count` for free. Soft delete
  + audit fields auto-populated.
- **`SingleEntityMapper<T>`** — programmatic CRUD without a `@Mapper` interface
  or any XML. One line per entity in a `@Configuration` class.
- **Retrofit + OkHttp** for external HTTP services. Hosts are resolved from the
  `clients.<name>.host` key, expected to be supplied by **Apollo** at runtime.
  MyBatis-style `@EnableRetrofitClients` auto-registers interfaces.
- **SLF4J-style JSON facade** — application code uses `JsonHelper`, never
  `ObjectMapper` / `JSON` directly. Both `jackson` and `fastjson2` adapters ship
  in this template; swap via the `json.implementation` config key.
- **`JsonHelper.toList(String, Class<T>)`** — JSON array → typed list without
  `TypeReference` boilerplate.
- **`FileHelper`** — every read / write / create of project files goes through
  one utility class.
- **JUnit 5 + Mockito + MockWebServer** for unit, slice, and integration tests.

## Package Layout

```
src/main/java/com/example/template/
├── Application.java                  # @SpringBootApplication entry point
├── client/                           # External HTTP calls (Retrofit)
│   ├── RetrofitClient.java           # @interface marker
│   ├── EnableRetrofitClients.java    # @interface, MyBatis-style scanner trigger
│   ├── ClientProperties.java         # per-client config (host + timeouts)
│   ├── ClientHostResolver.java       # legacy (kept for documentation)
│   ├── OkHttpHelper.java             # ad-hoc OkHttp wrapper
│   ├── RetrofitClientFactoryBean.java # FactoryBean that backs each @RetrofitClient
│   ├── RetrofitClientRegistrar.java  # ImportBeanDefinitionRegistrar (the scanner)
│   ├── JsonHelperConverterFactory.java # Retrofit JSON converter (uses JsonHelper)
│   └── DemoClient.java               # sample @RetrofitClient interface
├── common/
│   └── TypeReference.java            # project-level generic type capture
├── config/
│   ├── JsonConfig.java               # binds json.implementation -> JsonHelper
│   ├── MyBatisConfig.java
│   ├── ApolloConfig.java             # documentation of app.apollo.* keys
│   └── SingleEntityMapperConfig.java # @Bean methods for SingleEntityMapper
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
│   └── UserMapper.java               # extends BaseMapper<User> + custom methods
├── mybatis/                          # ← MyBatis helper classes
│   ├── Table.java                    # @Table("users")
│   ├── Column.java                   # @Column override
│   ├── Id.java                       # @Id
│   ├── Ignore.java                   # @Ignore (skip field in CRUD)
│   ├── LogicDelete.java              # @LogicDelete (soft delete)
│   ├── CreatedAt.java                # @CreatedAt
│   ├── UpdatedAt.java                # @UpdatedAt
│   ├── CreatedBy.java                # @CreatedBy
│   ├── UpdatedBy.java                # @UpdatedBy
│   ├── AuditorProvider.java          # supplies "now" + "current user"
│   ├── EntityField.java              # cached reflection metadata per field
│   ├── EntityMeta.java               # cached reflection metadata per entity
│   ├── SqlProvider.java              # static SQL builders
│   ├── MapperBridge.java             # @XxxProvider-annotated abstract methods
│   ├── BaseMapper.java               # interface-style CRUD (T bound via generic)
│   └── SingleEntityMapper.java       # programmatic CRUD (no @Mapper interface)
├── service/
│   ├── UserService.java
│   └── impl/UserServiceImpl.java
└── util/
    ├── JsonHelper.java               # SLF4J-style JSON facade
    ├── FileHelper.java               # all file I/O
    └── json/
        ├── JsonAdapter.java          # internal SPI
        ├── JacksonJsonAdapter.java
        ├── Fastjson2JsonAdapter.java
        └── JsonException.java
```

---

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

// pretty-print
String pretty = JsonHelper.toPrettyJson(user);
```

`toList(String, Class<T>)` is the recommended shortcut for the common
"JSON array → typed list" case. Reach for `TypeReference` only when the element
type is itself generic (nested maps, parameterized DTOs, etc.).

### Choosing the right API

| Need | Use |
| --- | --- |
| Serialize | `JsonHelper.toJson(Object)` |
| Deserialize a plain class | `JsonHelper.fromJson(String, Class)` |
| Deserialize `List<MyClass>` | `JsonHelper.toList(String, Class)` ← shortcut |
| Deserialize `List<Map<String, MyClass>>` | `JsonHelper.toList(String, TypeReference)` |
| Other deeply generic types | `JsonHelper.fromJson(String, TypeReference)` |

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

---

## External HTTP Calls (Retrofit)

External HTTP services are declared as Java interfaces and auto-registered as
Spring beans via `@EnableRetrofitClients` — the same pattern as MyBatis's
`@MapperScan`. Host, timeouts, and logging flag are read from
`clients.<clientName>.*` (sourced from `application.yml` or Apollo).

### Step 1 — annotate the entity with the table

`client/DemoClient.java`:
```java
@RetrofitClient(clientName = "demo-client")
public interface DemoClient {
    @GET("/api/health")
    Call<String> health();

    @GET("/api/users/{id}")
    Call<String> getUser(@Path("id") long id);
}
```

### Step 2 — register the scan package once

`Application.java`:
```java
@SpringBootApplication
@EnableRetrofitClients("com.example.template.client")
public class Application { ... }
```

### Step 3 — configure the host in `application.yml` (or Apollo)

```yaml
clients:
  demo-client:
    host: http://localhost:9090
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
    write-timeout-ms: 5000
    enable-logging: true
```

### Step 4 — inject directly, no factory needed

```java
@Service
@RequiredArgsConstructor
public class DemoService {
    private final DemoClient demoClient;          // ← auto-injected
    private final OkHttpHelper http;              // for ad-hoc calls

    public String fetch() throws IOException {
        return demoClient.getUser(1L).execute().body();
    }
}
```

### One-off requests

For calls that don't justify a typed interface, use `OkHttpHelper` — it shares
the same `clients.<name>.*` config block:

```java
@Autowired OkHttpHelper http;

// Uses clients.demo-client.host + path:
List<UserDto> users = http.get("demo-client", "/api/users", UserDto.class);
```

### Adding a new client

1. Create the interface in the `client/` package and annotate it with
   `@RetrofitClient(clientName = "my-service")`.
2. Add `clients.my-service.host=...` in application.yml / Apollo.

No Java registration code is needed — the scanner picks it up automatically.

---

## MyBatis CRUD — Annotation-Driven

The `mybatis` package ships an annotation-driven CRUD layer. Two flavors:

| Flavor | Class | Use when |
| --- | --- | --- |
| Interface-based | `BaseMapper<T>` | You want a `@Mapper` interface per entity |
| Programmatic | `SingleEntityMapper<T>` | You want a Spring bean without writing interfaces or XML |

Both share the same `SqlProvider` for SQL generation, the same `EntityMeta` for
reflection caching, and the same `AuditorProvider` for audit-field population.

### Step 1 — annotate the entity

`entity/User.java`:
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("users")
public class User implements Serializable {

    @Id
    private Long id;

    @Column("username")         // optional — overrides default snake_case mapping
    private String username;

    private String email;
    private Integer age;

    @CreatedAt                   // auto-filled with AuditorProvider.now() on insert/update
    private LocalDateTime createdAt;

    @UpdatedAt                   // auto-filled on update only
    private LocalDateTime updatedAt;

    @CreatedBy                   // auto-filled with AuditorProvider.currentUser()
    private String createdBy;

    @UpdatedBy                   // auto-filled on update only
    private String updatedBy;

    @LogicDelete                 // 0 = active, 1 = soft-deleted (overridable)
    private Integer deleted;

    @Ignore                      // never persisted, never in SQL
    private transient String fullName;
}
```

Annotation reference:

| Annotation | Purpose | Default behaviour |
| --- | --- | --- |
| `@Table("name")` | Required on the entity class | Maps to the given table |
| `@Column("col")` | Optional override | Default is `camelCase → snake_case` |
| `@Id` | Required on the primary-key field | Single-column primary key |
| `@Ignore` | Skip the field in all CRUD SQL | `transient` is also skipped |
| `@LogicDelete` | Marks the soft-delete column | Values 0 / 1; 0 = active |
| `@CreatedAt` | Creation timestamp | Stamped on insert |
| `@UpdatedAt` | Last-update timestamp | Stamped on insert + update |
| `@CreatedBy` | Creator identifier | Stamped on insert |
| `@UpdatedBy` | Last-modifier identifier | Stamped on insert + update |

### Step 2a — Interface-based usage (`BaseMapper<T>`)

`mapper/UserMapper.java`:
```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // BaseMapper provides: insert / updateById / deleteById / hardDeleteById /
    //                     selectById / selectByIds / selectAll / count

    // Custom queries live alongside, with their own SQL in UserMapper.xml:
    List<User> findByEmailLike(@Param("pattern") String pattern);
}
```

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    public User create(User u) { userMapper.insert(u); return u; }
    public User get(Long id)  { return userMapper.selectById(id); }
    public List<User> list()  { return userMapper.selectAll(); }
    public void delete(Long id) { userMapper.deleteById(id); }   // soft delete
    public void purge(Long id)  { userMapper.hardDeleteById(id); } // real delete
    public long count()         { return userMapper.count(); }
}
```

### Step 2b — Programmatic usage (`SingleEntityMapper<T>`)

If you don't want a `@Mapper` interface, declare the bean in a `@Configuration`:

`config/SingleEntityMapperConfig.java`:
```java
@Configuration
public class SingleEntityMapperConfig {
    @Bean
    public SingleEntityMapper<User> userSingleEntityMapper(SqlSessionFactory factory) {
        return new SingleEntityMapper<>(User.class, factory);
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final SingleEntityMapper<User> userMapper;   // ← direct Spring bean

    public User get(Long id)  { return userMapper.selectById(id); }
    public void delete(Long id) { userMapper.deleteById(id); }
}
```

### Audit provider

`AuditorProvider` supplies the values that fill `@CreatedAt`, `@UpdatedAt`,
`@CreatedBy`, `@UpdatedBy`. The default uses `LocalDateTime.now()` and
the literal string `"system"`.

For real applications, register a custom provider at startup — typically
pulling the current user from Spring Security's `SecurityContextHolder`:

```java
@Component
class SpringSecurityAuditorProvider implements AuditorProvider {
    @Override public LocalDateTime now() { return LocalDateTime.now(); }
    @Override public Object currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
```

`BaseMapper.setAuditorProvider(...)` and `SingleEntityMapper` use the same
provider, so one registration covers both flavours.

### Schema (H2 in tests, MySQL in prod)

`src/test/resources/schema.sql`:
```sql
CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64) NOT NULL,
    email       VARCHAR(128),
    age         INT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    deleted     INT NOT NULL DEFAULT 0
);
```

The MySQL DDL for production lives in `src/main/resources/mock/schema-mysql.sql`.

### Choosing between `BaseMapper` and `SingleEntityMapper`

| Scenario | Use |
| --- | --- |
| Single entity, pure CRUD, minimum files | `SingleEntityMapper<T>` |
| Entity needs custom SQL (joins, sub-queries, bulk) | `BaseMapper<T>` + custom methods |
| Mix of both | Either — they coexist in the same project |

---

## File I/O

All file operations go through `FileHelper`:

```java
FileHelper.writeText("config/app.yml", yaml);
String yaml = FileHelper.readText("config/app.yml");
List<String> lines = FileHelper.readLines("config/app.yml");
byte[] data = FileHelper.readBytes("bin/blob.bin");
FileHelper.appendText("log/run.log", "started\n");
FileHelper.writeBytes("out.bin", new byte[] {1, 2, 3});
FileHelper.copy("src.txt", "dst.txt");
FileHelper.move("old.txt", "new.txt");
FileHelper.delete("temp.tmp");                     // file
FileHelper.deleteRecursively("build/");            // file or directory tree
FileHelper.createIfAbsent("config/app.yml");
FileHelper.mkdirs("a/b/c");
FileHelper.openInputStream("data.csv");             // streaming
```

`FileHelper` wraps `IOException` as an unchecked `FileHelperException`. Default
encoding is UTF-8.

---

## Running

```bash
# build
mvn clean package

# run with the dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# run the integration tests
mvn test
```

## Apollo

This template assumes Apollo is the source of truth for `clients.*.host`,
`users.*.created_by` and any `app.*` settings. Apollo's Spring Boot starter is
**not** bundled to keep the dependency surface minimal — add
`apollo-spring-boot-starter` and `@EnableApolloConfig` on a config class to
wire it in. The `RetrofitClientFactoryBean` and `BaseMapper` / `SingleEntityMapper`
all read configuration via Spring's `Environment`, so Apollo overrides flow
transparently.

## IDE

An IntelliJ IDEA 2025.3 install is configured (see `~/.local/bin/idea`).
`IntelliJ IDEA → Open` the project root; Maven and JDK 21 are auto-detected.