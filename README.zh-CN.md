# Spring Boot 3 后端模板 (中文)

一个规范的 Spring Boot 3 后端模板。特点:

- JSON 库通过门面 (facade) 隔离,业务代码不直接依赖 Jackson/Fastjson2 的 API
- 文件 I/O 全部走统一的 `FileHelper`
- 外部 HTTP 调用统一通过 Retrofit,host 配置由 Apollo 提供
- 内置 MyBatis 增强层:用注解自动生成单表 CRUD,支持软删除与审计字段
- 提供两种 CRUD 风格:`BaseMapper<T>`(接口风格) 与 `SingleEntityMapper<T>`(编程式风格)

> 英文版见 [README.md](./README.md)。两份文档保持同步,内容一致。

## 特性一览

- **Spring Boot 3.2** + **Java 17**
- **MyBatis** 持久化,测试使用 H2 内存数据库
- **`BaseMapper<T>`** — 注解驱动的单表 CRUD。`selectById` / `selectAll` / `insert` /
  `updateById` / `deleteById` / `count` 等开箱即用,软删除与审计字段自动处理
- **`SingleEntityMapper<T>`** — 不写 `@Mapper` 接口、不写 XML,直接 `@Autowired` 即可使用
- **Retrofit + OkHttp** — 外部 HTTP 客户端。Host 由 `clients.<name>.host` 配置(本地从
  `application.yml`,生产从 Apollo)。提供 `@EnableRetrofitClients` 自动扫描注册
- **SLF4J 风格 JSON 门面** — 业务代码使用 `JsonHelper`,绝不直接 `import` Jackson/Fastjson2。
  通过 `json.implementation` 配置一键切换实现
- **`JsonHelper.toList(String, Class<T>)`** — JSON 数组一键转 `List<T>`,无需写 `TypeReference` 模板代码
- **`FileHelper`** — 项目内所有文件读写都走这一个工具类
- **JUnit 5 + Mockito + MockWebServer** 单元测试、切片测试、集成测试

## 包结构

```
src/main/java/com/example/template/
├── Application.java                  # @SpringBootApplication 启动类
├── client/                           # 外部 HTTP 调用 (Retrofit)
│   ├── RetrofitClient.java           # @interface 标记
│   ├── EnableRetrofitClients.java    # @interface, MyBatis 风格的扫描触发器
│   ├── ClientProperties.java         # 每个 client 的配置(host + 超时)
│   ├── OkHttpHelper.java             # 即席 OkHttp 封装
│   ├── RetrofitClientFactoryBean.java # 每个 @RetrofitClient 的 FactoryBean
│   ├── RetrofitClientRegistrar.java  # ImportBeanDefinitionRegistrar (扫描器)
│   ├── JsonHelperConverterFactory.java # Retrofit 的 JSON 转换器(用 JsonHelper)
│   └── DemoClient.java               # @RetrofitClient 示例接口
├── common/
│   └── TypeReference.java            # 项目级的泛型类型捕获
├── config/
│   ├── JsonConfig.java               # 绑定 json.implementation -> JsonHelper
│   ├── MyBatisConfig.java
│   ├── ApolloConfig.java             # app.apollo.* 配置说明
│   └── SingleEntityMapperConfig.java # SingleEntityMapper 的 @Bean 声明
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
│   └── UserMapper.java               # extends BaseMapper<User> + 自定义方法
├── mybatis/                          # ← MyBatis 增强层
│   ├── Table.java                    # @Table("users")
│   ├── Column.java                   # @Column 覆盖
│   ├── Id.java                       # @Id
│   ├── Ignore.java                   # @Ignore (CRUD 中跳过此字段)
│   ├── LogicDelete.java              # @LogicDelete (软删除)
│   ├── CreatedAt.java                # @CreatedAt
│   ├── UpdatedAt.java                # @UpdatedAt
│   ├── CreatedBy.java                # @CreatedBy
│   ├── UpdatedBy.java                # @UpdatedBy
│   ├── AuditorProvider.java          # 提供 "now" 与 "current user"
│   ├── EntityField.java              # 每个字段的反射元数据(已缓存)
│   ├── EntityMeta.java               # 每个实体的反射元数据(已缓存)
│   ├── SqlProvider.java              # SQL 静态构建器
│   ├── MapperBridge.java             # @XxxProvider 注解的抽象方法
│   ├── BaseMapper.java               # 接口式 CRUD (T 通过泛型绑定)
│   └── SingleEntityMapper.java       # 编程式 CRUD (不需要 @Mapper 接口)
├── service/
│   ├── UserService.java
│   └── impl/UserServiceImpl.java
└── util/
    ├── JsonHelper.java               # SLF4J 风格 JSON 门面
    ├── FileHelper.java               # 所有文件 I/O
    └── json/
        ├── JsonAdapter.java          # 内部 SPI
        ├── JacksonJsonAdapter.java
        ├── Fastjson2JsonAdapter.java
        └── JsonException.java
```

---

## JSON 用法

业务代码 **不得** 直接 `import com.fasterxml.jackson.*` 或 `com.alibaba.fastjson2.*`。
所有 JSON 操作都通过 `JsonHelper`:

```java
// 普通类
String json = JsonHelper.toJson(user);
UserDto user = JsonHelper.fromJson(json, UserDto.class);

// JSON 数组 → List<T>(最常见场景, 不需要写 TypeReference)
List<UserDto> users = JsonHelper.toList(json, UserDto.class);

// 深层泛型元素类型(如 List<Map<String, UserDto>>)
TypeReference<Map<String, UserDto>> elem = new TypeReference<>() {};
List<Map<String, UserDto>> rows = JsonHelper.toList(json, elem);

// 美化输出
String pretty = JsonHelper.toPrettyJson(user);
```

`toList(String, Class<T>)` 是 "JSON 数组 → 类型化列表" 场景的推荐快捷方式。
只有当元素类型本身是泛型(嵌套 Map、参数化 DTO 等)时,才用 `TypeReference` 重载。

### API 选择对照表

| 需求 | 使用 |
| --- | --- |
| 序列化 | `JsonHelper.toJson(Object)` |
| 反序列化普通类 | `JsonHelper.fromJson(String, Class)` |
| 反序列化 `List<MyClass>` | `JsonHelper.toList(String, Class)` ← 快捷方式 |
| 反序列化 `List<Map<String, MyClass>>` | `JsonHelper.toList(String, TypeReference)` |
| 其它深层泛型 | `JsonHelper.fromJson(String, TypeReference)` |

### 切换实现

通过 `application.yml` 配置当前激活的适配器:

```yaml
json:
  implementation: jackson   # 或 fastjson2
```

也可以在运行时切换(测试或 feature flag 场景):

```java
JsonHelper.setAdapter(new Fastjson2JsonAdapter());
```

### 添加新实现

1. 实现 `util/json/JsonAdapter.java`。
2. 在 `JsonHelper.setAdapterByName(...)` 中注册名称。

---

## 外部 HTTP 调用 (Retrofit)

外部 HTTP 服务以 Java 接口的形式声明,通过 `@EnableRetrofitClients` 自动注册为
Spring Bean — 模式与 MyBatis 的 `@MapperScan` 相同。Host、超时、日志开关都从
`clients.<clientName>.*` 读取(从 `application.yml` 或 Apollo 注入)。

### 第 1 步 — 在接口上加注解

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

### 第 2 步 — 在启动类上启用扫描

`Application.java`:
```java
@SpringBootApplication
@EnableRetrofitClients("com.example.template.client")
public class Application { ... }
```

### 第 3 步 — 在 `application.yml` (或 Apollo) 中配置 host

```yaml
clients:
  demo-client:
    host: http://localhost:9090
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
    write-timeout-ms: 5000
    enable-logging: true
```

### 第 4 步 — 直接注入,无需工厂

```java
@Service
@RequiredArgsConstructor
public class DemoService {
    private final DemoClient demoClient;          // ← 自动注入
    private final OkHttpHelper http;              // 即席请求用

    public String fetch() throws IOException {
        return demoClient.getUser(1L).execute().body();
    }
}
```

### 即席请求

不值得专门定义接口的临时调用,使用 `OkHttpHelper` — 它共用同一套
`clients.<name>.*` 配置:

```java
@Autowired OkHttpHelper http;

// 使用 clients.demo-client.host + path:
List<UserDto> users = http.get("demo-client", "/api/users", UserDto.class);
```

### 新增一个 client

1. 在 `client/` 包下创建接口,加上 `@RetrofitClient(clientName = "my-service")`。
2. 在 `application.yml` / Apollo 中添加 `clients.my-service.host=...`。

不需要任何 Java 注册代码 — 扫描器会自动发现并注册。

---

## MyBatis CRUD — 注解驱动

`mybatis` 包提供了一套基于注解的 CRUD 层。两种风格:

| 风格 | 类 | 使用场景 |
| --- | --- | --- |
| 接口式 | `BaseMapper<T>` | 每个实体一个 `@Mapper` 接口 |
| 编程式 | `SingleEntityMapper<T>` | 不想写接口或 XML,直接用 Spring Bean |

两者共用 `SqlProvider` 生成 SQL、共用 `EntityMeta` 缓存反射元数据、共用
`AuditorProvider` 填充审计字段。

### 第 1 步 — 在实体类上加注解

`entity/User.java`:
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("users")
public class User implements Serializable {

    @Id
    private Long id;

    @Column("username")         // 可选 — 默认驼峰转下划线
    private String username;

    private String email;
    private Integer age;

    @CreatedAt                   // 插入时自动填充 AuditorProvider.now()
    private LocalDateTime createdAt;

    @UpdatedAt                   // 插入和更新时填充
    private LocalDateTime updatedAt;

    @CreatedBy                   // 插入时自动填充 AuditorProvider.currentUser()
    private String createdBy;

    @UpdatedBy                   // 插入和更新时填充
    private String updatedBy;

    @LogicDelete                 // 0 = 有效, 1 = 软删除(可改)
    private Integer deleted;

    @Ignore                      // 不入库,不出现在 SQL 中
    private transient String fullName;
}
```

#### 注解速查

| 注解 | 作用 | 默认行为 |
| --- | --- | --- |
| `@Table("name")` | 必填,标注实体类 | 映射到给定表名 |
| `@Column("col")` | 可选,覆盖列名 | 默认 `驼峰 → 下划线` |
| `@Id` | 必填,标注主键字段 | 单列主键 |
| `@Ignore` | 在所有 CRUD SQL 中跳过此字段 | `transient` 字段也会被跳过 |
| `@LogicDelete` | 软删除列 | 0 / 1;0 = 有效 |
| `@CreatedAt` | 创建时间戳 | 插入时填充 |
| `@UpdatedAt` | 最后更新时间戳 | 插入与更新时填充 |
| `@CreatedBy` | 创建者标识 | 插入时填充 |
| `@UpdatedBy` | 最后修改者标识 | 插入与更新时填充 |

### 第 2 步 (风格 A) — 接口式用法 `BaseMapper<T>`

`mapper/UserMapper.java`:
```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // BaseMapper 提供: insert / updateById / deleteById / hardDeleteById /
    //                 selectById / selectByIds / selectAll / count

    // 自定义查询放在这里,SQL 写在 UserMapper.xml:
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
    public void delete(Long id) { userMapper.deleteById(id); }    // 软删除
    public void purge(Long id)  { userMapper.hardDeleteById(id); } // 真删除
    public long count()         { return userMapper.count(); }
}
```

### 第 2 步 (风格 B) — 编程式用法 `SingleEntityMapper<T>`

不想写 `@Mapper` 接口,在 `@Configuration` 里直接声明 Bean:

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
    private final SingleEntityMapper<User> userMapper;   // ← 直接 Spring Bean

    public User get(Long id)  { return userMapper.selectById(id); }
    public void delete(Long id) { userMapper.deleteById(id); }
}
```

### 审计字段提供者 `AuditorProvider`

`AuditorProvider` 提供填充 `@CreatedAt` / `@UpdatedAt` / `@CreatedBy` / `@UpdatedBy`
所需的值。默认实现用 `LocalDateTime.now()` 与字面量字符串 `"system"`。

实际项目中,通常在启动时注册一个自定义实现 — 比如从 Spring Security 的
`SecurityContextHolder` 取当前用户:

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

`BaseMapper.setAuditorProvider(...)` 与 `SingleEntityMapper` 共用同一个
provider,所以一次注册同时覆盖两种风格。

### Schema (测试用 H2,生产用 MySQL)

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

生产环境的 MySQL DDL 在 `src/main/resources/mock/schema-mysql.sql`。

### `BaseMapper` 与 `SingleEntityMapper` 怎么选?

| 场景 | 用 |
| --- | --- |
| 单一实体,纯 CRUD,文件越少越好 | `SingleEntityMapper<T>` |
| 实体需要自定义 SQL(JOIN、子查询、批量) | `BaseMapper<T>` + 自定义方法 |
| 两者都有 | 任选其一 — 它们在同一个项目里可以共存 |

---

## 文件 I/O

项目里所有文件操作都通过 `FileHelper`:

```java
FileHelper.writeText("config/app.yml", yaml);
String yaml = FileHelper.readText("config/app.yml");
List<String> lines = FileHelper.readLines("config/app.yml");
byte[] data = FileHelper.readBytes("bin/blob.bin");
FileHelper.appendText("log/run.log", "started\n");
FileHelper.writeBytes("out.bin", new byte[] {1, 2, 3});
FileHelper.copy("src.txt", "dst.txt");
FileHelper.move("old.txt", "new.txt");
FileHelper.delete("temp.tmp");                     // 文件
FileHelper.deleteRecursively("build/");            // 文件或目录树
FileHelper.createIfAbsent("config/app.yml");
FileHelper.mkdirs("a/b/c");
FileHelper.openInputStream("data.csv");             // 流式读取
```

`FileHelper` 将 `IOException` 包装为非受检的 `FileHelperException`。默认编码 UTF-8。

---

## 运行

```bash
# 编译打包
mvn clean package

# 用 dev 配置运行
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 运行所有测试
mvn test
```

## Apollo 配置中心

模板默认假设 Apollo 是 `clients.*.host`、审计字段的"创建者/修改者"以及所有
`app.*` 配置的来源。Apollo Spring Boot Starter **没有**打包进来(为了保持依赖
最小),需要时自行添加 `apollo-spring-boot-starter` 并在配置类上加 `@EnableApolloConfig`。
`RetrofitClientFactoryBean`、`BaseMapper`、`SingleEntityMapper` 都通过 Spring 的
`Environment` 读配置,所以 Apollo 覆盖值会自动生效,无需修改业务代码。

## IDE

IntelliJ IDEA 2025.3 已配置好(见 `~/.local/bin/idea`)。`IntelliJ IDEA → Open` 选
项目根目录即可,Maven 与 JDK 21 会被自动识别。

> 本文档为中文版,与 [README.md](./README.md) 保持同步。