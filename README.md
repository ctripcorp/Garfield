# Garfield

Multi-storage orchestration framework — configuration-driven leader/follower writes across multiple storage media, adaptable to any storage technology via SPI, with built-in eventual consistency guarantees.

---

## Core Features

- **Config-driven routing**: A single JSON file defines `reqClassName → leader / follower` multi-media routing, with hot-reload support
- **Multi-media orchestration**: Leader storage writes synchronously (with retry); follower storage writes asynchronously with automatic compensation on failure
- **Eventual consistency**: Failed follower writes automatically publish compensation messages; the consumer side provides an orchestrator, pluggable backoff strategy, and exhaustion handler
- **Two orthogonal dimensions**: `EngineCapability` (data access pattern) × `StorageType` (storage medium) freely combined
- **Fully SPI-based**: Circuit breaker, distributed lock, metrics reporting, compensation channel, and backoff strategy are all pluggable interfaces; all built-in beans use `@ConditionalOnMissingBean`

---

## Architecture

### Module Structure

```
garfield/
├── garfield-common               # Foundation: SPI interfaces + DTOs + config model + default impls
├── garfield-engine               # Engine: storage engine capability interfaces + circuit-breaker base classes
├── garfield-transfer             # Transfer: bidirectional conversion between business DTOs and engine wrappers
├── garfield-process              # Process: write/read orchestration + routing + compensation consumption
├── garfield-spring-boot-starter  # Spring Boot auto-configuration
└── garfield-example              # Example: Redis + Kafka order storage
```

### Module Dependencies (unidirectional, no cycles)

```
common (Resilience4j optional, Redisson optional)
  ↑
engine → common
  ↑
transfer → common, engine
  ↑
process → common, engine, transfer
  ↑
spring-boot-starter → process, Spring Boot
  ↑
example → starter, Jedis, Kafka
```

### Layered Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application code                         │
│  Controller / Service → WriteOrchestrator / ReadOrchestrator    │
├─────────────────────────────────────────────────────────────────┤
│                     garfield-process                            │
│  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐ │
│  │ StorageRoute  │  │WriteOrchestrator │  │CompensationOrch.   │ │
│  │ (leader/follower)│  │ (sync leader     │  │ (backoff + retry   │ │
│  │               │  │  async followers)   │  │  + exhaustion)     │ │
│  └──────┬───────┘  └───────┬──────────┘  └────────┬───────────┘ │
│         │                  │                      │              │
│  ┌──────┴──────────────────┴──────────────────────┘              │
│  │  StorageProcess (KV / HashKV / MQ / Service)                  │
├──┼───────────────────────────────────────────────────────────────┤
│  │               garfield-transfer                               │
│  │  KvTransfer / HashKvTransfer / MqTransfer / ServiceTransfer   │
├──┼───────────────────────────────────────────────────────────────┤
│  │               garfield-engine                                 │
│  │  StorageEngine + Capability interfaces (KvCapable / Hash...)  │
│  │  AbstractKvEngine / AbstractHashEngine (circuit-breaker wrap) │
├──┼───────────────────────────────────────────────────────────────┤
│  │               garfield-common                                 │
│  │  SPI: CircuitBreaker, DistributedLock, MetricsReporter,      │
│  │       CompensationChannel, BackoffStrategy, ...               │
│  │  Config: JSON file + WatchService hot-reload                  │
└──┴───────────────────────────────────────────────────────────────┘
```

---

## Core Concepts

### EngineCapability × StorageType

Garfield models data access along two orthogonal dimensions:


| Dimension       | Meaning              | Examples                                                                   |
| --------------- | -------------------- | -------------------------------------------------------------------------- |
| **EngineCapability** | How data is accessed | `KV` (key-value), `HASH` (hash), `MESSAGE` (messaging), `SERVICE_CALL` (RPC), `TOUCH`, `SCAN`, `QUERY` |
| **StorageType** | Where data is stored | `REDIS`, `MYSQL`, `KAFKA`, `ELASTICSEARCH`, `GRPC`, etc.                   |


Combination matrix:

```
                  StorageType
                  REDIS    MYSQL    KAFKA    GRPC
EngineCapability┌────────┬────────┬────────┬────────┐
  KV            │   ✓    │   ✓    │   ✗    │   ✗    │
  HASH          │   ✓    │   ✗    │   ✗    │   ✗    │
  MESSAGE       │   ✗    │   ✗    │   ✓    │   ✗    │
  SERVICE_CALL  │   ✗    │   ✗    │   ✗    │   ✓    │
                └────────┴────────┴────────┴────────┘
```

### Routing Model (StorageRoute)

Each `reqClassName` maps to one route containing:

- **leader**: primary medium — synchronous write; failure is returned directly to the caller
- **followers**: list of secondary media — asynchronous writes; failure triggers the compensation channel

### Write Flow

```
Write request → WriteOrchestrator.batchPut(context)
  │
  ├─ 1. LockOrchestrator.batchCheckLocks()     ← optional (DistributedLock SPI, partial/all-or-nothing mode)
  ├─ 2. leaderProcess.write()                  ← synchronous write with built-in retry
  └─ 3. followerProcess[].write() [async]         ← async write with timeout (orTimeout + compensation on failure)
       └─ failure/timeout → CompensationChannel.publish()  ← publish compensation message
```

### Compensation & Retry

When a follower write fails, a compensation message enters the message channel. The consumer side is orchestrated by `CompensationOrchestrator`:

```
Compensation message arrives (from MQ / local queue)
  │
  ▼
CompensationOrchestrator.process(message, attempt)
  │
  ├─ 1. Look up CompensationHandler by reqClassName
  ├─ 2. handler.getCompensateDataList()    ← business filtering / data extraction
  ├─ 3. handler.writeData()                ← execute compensating write (via WriteOrchestrator by default)
  │
  ├─ success → SUCCESS
  └─ failure
      ├─ not exhausted → BackoffStrategy computes delay → return NEED_RETRY (caller schedules retry per CompensationResult)
      └─ exhausted     → CompensationExhaustionHandler handles → EXHAUSTED
```

**Built-in backoff strategies**:


| Strategy                       | Description                                                                          |
| ------------------------------ | ------------------------------------------------------------------------------------ |
| `ExponentialBackoffStrategy`   | Exponential backoff + jitter; default `2s → 4s → 8s → ... → 45h`, ±10% random jitter |
| `FixedIntervalBackoffStrategy` | Fixed-interval backoff                                                               |


---

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.ctrip.garfield</groupId>
    <artifactId>garfield-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Write `garfield-config.json`

```json
{
  "storageEngineConfigs": [
    {
      "storageId": "redis_leader",
      "storageType": "REDIS",
      "enabled": true,
      "version": 1,
      "properties": { "host": "localhost", "port": 6379 }
    },
    {
      "storageId": "kafka_follower",
      "storageType": "KAFKA",
      "enabled": true,
      "version": 1,
      "properties": { "bootstrap.servers": "localhost:9092", "topic": "order-events" }
    }
  ],
  "processConfigs": [
    {
      "reqClassName": "OrderDataUnit",
      "version": 1,
      "leaderProcess": {
        "engineId": "redis_leader",
        "processType": "KV",
        "transferName": "orderKvTransfer"
      },
      "followerProcess": [
        {
          "engineId": "kafka_follower",
          "processType": "MESSAGE",
          "transferName": "orderMqTransfer"
        }
      ]
    }
  ]
}
```

### 3. Implement a storage engine

```java
// Implement StorageEngine + capability interface
public class JedisKvEngine extends AbstractKvEngine<OrderKvWrapper> {
    @Override protected OperationResult<?> doBatchPut(List<OrderKvWrapper> wrappers) { /* Jedis SET */ }
    @Override protected OperationResult<OrderKvWrapper> doBatchGet(List<OrderKvWrapper> wrappers) { /* Jedis GET */ }
    // ...
}

// Register the engine factory
@Component
public class JedisEngineFactory implements StorageEngineFactory {
    @Override public StorageType storageType() { return GarfieldStorageType.REDIS; }
    @Override public Set<EngineCapability> supportedEngineCapabilities() { return Set.of(EngineCapability.KV, EngineCapability.HASH); }
    @Override public StorageEngine createEngine(StorageEngineConfig config) { return new JedisKvEngine(config); }
}
```

### 4. Implement a data transfer

```java
@Component("orderKvTransfer")
public class OrderKvTransfer implements KvTransfer<OrderDataUnit, OrderDataUnit, OrderKvWrapper> {
    @Override public OrderKvWrapper toStorage(OrderDataUnit data) { /* DTO → Redis wrapper */ }
    @Override public List<OrderDataUnit> storageToObject(OrderKvWrapper wrapper) { /* Redis wrapper → DTO */ }
}
```

### 5. Configure `application.yml`

```yaml
garfield:
  config-path: classpath:garfield-config.json
  follower:
    timeout-ms: 5000   # follower write timeout in ms (default 5000); timeout triggers compensation
```

### 6. Call the write API

```java
@RestController
public class OrderController {

    private final WriteOrchestrator writeOrchestrator;

    @PostMapping("/orders")
    public void createOrder(@RequestBody OrderDataUnit order) {
        GarfieldContext<OrderDataUnit, OrderFailureResult> context = new GarfieldContext<>();
        context.setReqClassName("OrderDataUnit");
        context.setDataInfos(List.of(order));
        writeOrchestrator.batchPut(context);
    }
}
```

See the `garfield-example` module for a complete working example.

---

## Concurrency Safety

- **Follower writes never block the main flow**: The default `FollowerExecutorProvider` uses `AbortPolicy` — when the queue is full, `RejectedExecutionException` triggers the compensation path instead of degrading to synchronous execution
- **Single compensation per follower timeout**: Each follower's timeout path and normal completion path are mutually exclusive via CAS (`AtomicBoolean`), ensuring exactly one compensation message is sent
- **JMM-safe distributed locks**: `LockEntity.locked` is `volatile`, guaranteeing visibility of parallel lock acquisition results to the main thread without relying on ForkJoinTask implementation details

---

## SPI Extension Points

All SPIs are registered with `@ConditionalOnMissingBean`; declare your own bean to override any default.


| SPI Interface                   | Default Implementation                                            | Description                                                                         |
| ------------------------------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `CircuitBreaker`                | `Resilience4jCircuitBreaker`                                      | Circuit-breaker protection (auto-enabled when Resilience4j is on the classpath)     |
| `DistributedLock`               | `RedissonDistributedLock`                                         | Distributed lock with token-based ownership; LockOrchestrator supports all-or-nothing and partial failure modes |
| `MetricsReporter`               | `Slf4jMetricsReporter`                                            | Observation-based metrics & audit reporting (recordWrite/recordRead/recordCompensation) |
| `CompensationChannel`           | `NoOpCompensationChannel`                                         | Compensation message publishing channel                                             |
| `BackoffStrategy`               | `ExponentialBackoffStrategy`                                      | Compensation retry backoff strategy; per-handler override via `CompensationHandler.backoffStrategy()` |
| `CompensationExhaustionHandler` | `LoggingExhaustionHandler`                                        | Handles retry exhaustion                                                            |
| `StorageEngineFactory`          | —                                                                 | Implemented by users to register custom storage engines                             |
| `StorageTypeRegistry`           | `DefaultStorageTypeRegistry`                                      | Aggregates valid `StorageType` values from all `StorageEngineFactory` beans         |
| `ConfigLoader`                  | User-provided (`FileConfigLoader` in example is a reference impl) | Bridges to your config center; the example demonstrates a file-based implementation |
| `GarfieldSerializer`            | `JacksonSerializer`                                               | Project-wide object ↔ byte[]/String serialization                                   |


---

## Integration Options


| Option                    | Description                                                                                      |
| ------------------------- | ------------------------------------------------------------------------------------------------ |
| **Zero-config**           | Import `garfield-spring-boot-starter` — one dependency covers everything                         |
| **Minimal**               | Import `common` + `engine` + `transfer` + `process` and implement all SPIs yourself              |
| **Partial customization** | Import `spring-boot-starter` and use Maven exclusions to remove unwanted default implementations |


---

## Tech Stack


| Item                        | Version                                                      |
| --------------------------- | ------------------------------------------------------------ |
| JDK                         | 21                                                           |
| Spring Boot                 | 3.2.x (starter layer only; core modules are Spring-agnostic) |
| Config format               | JSON file + JDK WatchService hot-reload                      |
| Build tool                  | Maven                                                        |
| Testing                     | JUnit 5 + Mockito                                            |
| Circuit breaker (optional)  | Resilience4j                                                 |
| Distributed lock (optional) | Redisson                                                     |


---

## Custom StorageType

Three steps to register your own storage medium:

1. **Define an enum implementing `StorageType`**
  ```java
   public enum MyStorageType implements StorageType { MY_CUSTOM_STORAGE }
  ```
2. **Write a `StorageEngineFactory`** whose `storageType()` returns your enum value. Spring auto-discovers it.
3. **Reference it by name in the config file** (leading/trailing whitespace and case are normalized):
  ```json
   { "storageType": "my_custom_storage", ... }
  ```

See the `garfield-example/custom/` package: `MyStorageType` + `CustomDemoEngine` + `CustomDemoEngineFactory`.

### Integrating a Config Center

Garfield does not auto-configure `ConfigLoader` — the application must supply a bean. The example uses `FileConfigLoader` as a reference. Below is a sketch for integrating with Apollo or any config center:

```java
public class ApolloConfigLoader implements ConfigLoader {
    private final Config apollo;
    private final String key;
    private final ObjectMapper om;

    public ApolloConfigLoader(Config apollo, String key, StorageTypeRegistry registry) {
        this.apollo = apollo;
        this.key = key;
        this.om = GarfieldObjectMappers.create(registry);   // normalization rules from the framework
    }

    @Override public StorageConfig load() {
        return om.readValue(apollo.getProperty(key, "{}"), StorageConfig.class);
    }

    @Override public void watch(Consumer<StorageConfig> cb) {
        apollo.addChangeListener(c -> { if (c.isChanged(key)) cb.accept(load()); });
    }
}
```

(This code lives in the application repository, not in garfield.)

---

## License

Apache License 2.0

---

---

# Garfield（中文文档）

多介质存储编排框架——用配置驱动 leader/follower 多介质写入，通过 SPI 对接任意存储技术，内置最终一致性保障。

---

## 核心特性

- **配置驱动路由**：一份 JSON 配置定义 `reqClassName → leader / follower` 的多介质路由，支持热加载
- **多介质编排**：主介质同步写入（带重试），从介质异步写入，失败自动补偿
- **最终一致性**：从介质写入失败时自动发送补偿消息，消费侧提供编排器 + 可插拔退避策略 + 重试耗尽处理
- **两个正交维度**：EngineCapability（数据访问模式）× StorageType（存储介质）自由组合
- **全 SPI 化**：熔断、分布式锁、指标上报、补偿通道、退避策略均为可插拔接口，所有 Bean 带 `@ConditionalOnMissingBean`

---

## 技术架构

### 模块结构

```
garfield/
├── garfield-common               # 基础层：SPI 接口 + DTO + 配置模型 + 默认实现
├── garfield-engine               # 引擎层：存储引擎能力接口 + 熔断包装基类
├── garfield-transfer             # 转换层：业务 DTO ↔ 引擎 Wrapper 双向转换
├── garfield-process              # 过程层：写/读编排 + 路由 + 补偿消费
├── garfield-spring-boot-starter  # Spring Boot 自动装配
└── garfield-example              # 示例：Redis + Kafka 订单存储
```

### 模块依赖（单向，无循环）

```
common (Resilience4j optional, Redisson optional)
  ↑
engine → common
  ↑
transfer → common, engine
  ↑
process → common, engine, transfer
  ↑
spring-boot-starter → process, Spring Boot
  ↑
example → starter, Jedis, Kafka
```

### 架构分层

```
┌─────────────────────────────────────────────────────────────────┐
│                        使用者代码                                │
│  Controller / Service → WriteOrchestrator / ReadOrchestrator    │
├─────────────────────────────────────────────────────────────────┤
│                     garfield-process                            │
│  ┌──────────────┐  ┌───────────────────┐  ┌───────────────────┐ │
│  │ StorageRoute  │  │WriteOrchestrator  │  │CompensationOrch.  │ │
│  │ (leader/follower)│  │ (同步主写          │  │ (补偿消费编排      │ │
│  │               │  │  异步从写)         │  │  退避+重试+耗尽)   │ │
│  └──────┬───────┘  └────────┬──────────┘  └────────┬──────────┘ │
│         │                   │                      │             │
│  ┌──────┴───────────────────┴──────────────────────┘             │
│  │  StorageProcess (KV / HashKV / MQ / Service)                  │
├──┼───────────────────────────────────────────────────────────────┤
│  │               garfield-transfer                               │
│  │  KvTransfer / HashKvTransfer / MqTransfer / ServiceTransfer   │
├──┼───────────────────────────────────────────────────────────────┤
│  │               garfield-engine                                 │
│  │  StorageEngine + Capability 接口 (KvCapable / HashCapable…)   │
│  │  AbstractKvEngine / AbstractHashEngine（内置熔断包装）          │
├──┼───────────────────────────────────────────────────────────────┤
│  │               garfield-common                                 │
│  │  SPI: CircuitBreaker, DistributedLock, MetricsReporter,      │
│  │       CompensationChannel, BackoffStrategy, ...               │
│  │  Config: JSON 文件驱动 + WatchService 热加载                   │
└──┴───────────────────────────────────────────────────────────────┘
```

---

## 核心概念

### EngineCapability × StorageType

Garfield 围绕两个正交维度建模：


| 维度              | 含义     | 示例                                                   |
| --------------- | ------ | ---------------------------------------------------- |
| **EngineCapability** | 数据怎么存取 | `KV`（键值）、`HASH`（哈希）、`MESSAGE`（消息）、`SERVICE_CALL`（服务调用）、`TOUCH`、`SCAN`、`QUERY` |
| **StorageType** | 数据存到哪里 | `REDIS`、`MYSQL`、`KAFKA`、`ELASTICSEARCH`、`GRPC` 等     |


组合示例：

```
                  StorageType
                  REDIS    MYSQL    KAFKA    GRPC
EngineCapability┌────────┬────────┬────────┬────────┐
  KV            │   ✓    │   ✓    │   ✗    │   ✗    │
  HASH          │   ✓    │   ✗    │   ✗    │   ✗    │
  MESSAGE       │   ✗    │   ✗    │   ✓    │   ✗    │
  SERVICE_CALL  │   ✗    │   ✗    │   ✗    │   ✓    │
                └────────┴────────┴────────┴────────┘
```

### 路由模型（StorageRoute）

每个 `reqClassName` 对应一条路由，包含：

- **leader**：主介质，同步写入，失败直接返回调用方
- **followers**：从介质列表，异步写入，失败走补偿消息通道

### 写入流程

```
写请求 → WriteOrchestrator.batchPut(context)
  │
  ├─ 1. LockOrchestrator.batchCheckLocks()     ← 可选（DistributedLock SPI，支持 partial/all-or-nothing 模式）
  ├─ 2. leaderProcess.write()                  ← 同步写入，内置重试
  └─ 3. followerProcess[].write() [async]         ← 异步写入，带超时保护（orTimeout + 超时/失败走补偿）
       └─ 失败/超时 → CompensationChannel.publish() ← 发送补偿消息
```

### 补偿重试机制

从介质写入失败后，补偿消息进入消息通道。消费侧由 `CompensationOrchestrator` 编排：

```
补偿消息到达（来自 MQ / 本地队列）
  │
  ▼
CompensationOrchestrator.process(message, attempt)
  │
  ├─ 1. 按 reqClassName 查找 CompensationHandler
  ├─ 2. handler.getCompensateDataList()    ← 业务过滤/提取补偿数据
  ├─ 3. handler.writeData()                ← 执行补偿写入（默认走 WriteOrchestrator）
  │
  ├─ 成功 → SUCCESS
  └─ 失败
      ├─ 未耗尽 → BackoffStrategy 计算延迟 → 返回 NEED_RETRY（调用方按 CompensationResult 安排重试）
      └─ 已耗尽 → CompensationExhaustionHandler 处理 → EXHAUSTED
```

**内置退避策略**：


| 策略                             | 说明                                                    |
| ------------------------------ | ----------------------------------------------------- |
| `ExponentialBackoffStrategy`   | 指数退避 + jitter，默认 `2s → 4s → 8s → ... → 45h`，±10% 随机抖动 |
| `FixedIntervalBackoffStrategy` | 固定间隔退避                                                |


---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.ctrip.garfield</groupId>
    <artifactId>garfield-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 编写配置文件 `garfield-config.json`

```json
{
  "storageEngineConfigs": [
    {
      "storageId": "redis_leader",
      "storageType": "REDIS",
      "enabled": true,
      "version": 1,
      "properties": { "host": "localhost", "port": 6379 }
    },
    {
      "storageId": "kafka_follower",
      "storageType": "KAFKA",
      "enabled": true,
      "version": 1,
      "properties": { "bootstrap.servers": "localhost:9092", "topic": "order-events" }
    }
  ],
  "processConfigs": [
    {
      "reqClassName": "OrderDataUnit",
      "version": 1,
      "leaderProcess": {
        "engineId": "redis_leader",
        "processType": "KV",
        "transferName": "orderKvTransfer"
      },
      "followerProcess": [
        {
          "engineId": "kafka_follower",
          "processType": "MESSAGE",
          "transferName": "orderMqTransfer"
        }
      ]
    }
  ]
}
```

### 3. 实现存储引擎

```java
// 实现 StorageEngine + 能力接口
public class JedisKvEngine extends AbstractKvEngine<OrderKvWrapper> {
    @Override protected OperationResult<?> doBatchPut(List<OrderKvWrapper> wrappers) { /* Jedis SET */ }
    @Override protected OperationResult<OrderKvWrapper> doBatchGet(List<OrderKvWrapper> wrappers) { /* Jedis GET */ }
    // ...
}

// 注册引擎工厂
@Component
public class JedisEngineFactory implements StorageEngineFactory {
    @Override public StorageType storageType() { return GarfieldStorageType.REDIS; }
    @Override public Set<EngineCapability> supportedEngineCapabilities() { return Set.of(EngineCapability.KV, EngineCapability.HASH); }
    @Override public StorageEngine createEngine(StorageEngineConfig config) { return new JedisKvEngine(config); }
}
```

### 4. 实现数据转换器

```java
@Component("orderKvTransfer")
public class OrderKvTransfer implements KvTransfer<OrderDataUnit, OrderDataUnit, OrderKvWrapper> {
    @Override public OrderKvWrapper toStorage(OrderDataUnit data) { /* DTO → Redis wrapper */ }
    @Override public List<OrderDataUnit> storageToObject(OrderKvWrapper wrapper) { /* Redis wrapper → DTO */ }
}
```

### 5. 配置 `application.yml`

```yaml
garfield:
  config-path: classpath:garfield-config.json
  follower:
    timeout-ms: 5000   # follower 写入超时（毫秒，默认 5000）；超时自动走补偿路径
```

### 6. 调用写入

```java
@RestController
public class OrderController {

    private final WriteOrchestrator writeOrchestrator;

    @PostMapping("/orders")
    public void createOrder(@RequestBody OrderDataUnit order) {
        GarfieldContext<OrderDataUnit, OrderFailureResult> context = new GarfieldContext<>();
        context.setReqClassName("OrderDataUnit");
        context.setDataInfos(List.of(order));
        writeOrchestrator.batchPut(context);
    }
}
```

完整示例参见 `garfield-example` 模块。

---

## 并发安全保证

- **Follower 写入不阻塞主流程**：默认 `FollowerExecutorProvider` 使用 `AbortPolicy`，队列满时通过 `RejectedExecutionException` 触发补偿路径，不会退化为同步写入
- **Follower 超时单次补偿**：每个 follower 的超时路径与正常完成路径通过 CAS（`AtomicBoolean`）互斥，确保只发送一次补偿消息
- **分布式锁 JMM 安全**：`LockEntity.locked` 为 `volatile`，并行锁获取结果对后续逻辑立即可见，不依赖 ForkJoinTask 实现细节

---

## SPI 扩展点

所有 SPI 均带 `@ConditionalOnMissingBean` 注册，自定义 Bean 可直接覆盖默认实现。


| SPI 接口                          | 默认实现                                      | 说明                                               |
| ------------------------------- | ----------------------------------------- | ------------------------------------------------ |
| `CircuitBreaker`                | `Resilience4jCircuitBreaker`              | 熔断保护（classpath 有 Resilience4j 时自动启用）             |
| `DistributedLock`               | `RedissonDistributedLock`                 | 分布式锁（token 所有权模式）；LockOrchestrator 支持 all-or-nothing 和 partial 两种失败策略 |
| `MetricsReporter`               | `Slf4jMetricsReporter`                    | 基于 Observation 上下文的指标与审计上报（recordWrite/recordRead/recordCompensation） |
| `CompensationChannel`           | `NoOpCompensationChannel`                 | 补偿消息发送通道                                         |
| `BackoffStrategy`               | `ExponentialBackoffStrategy`              | 补偿重试退避策略；可通过 `CompensationHandler.backoffStrategy()` 按 handler 覆盖 |
| `CompensationExhaustionHandler` | `LoggingExhaustionHandler`                | 重试耗尽处理                                           |
| `StorageEngineFactory`          | —                                         | 由使用方实现，注册自定义存储引擎                                 |
| `StorageTypeRegistry`           | `DefaultStorageTypeRegistry`              | 从所有 `StorageEngineFactory` bean 汇聚合法 storageType |
| `ConfigLoader`                  | 使用方提供（example 里 `FileConfigLoader` 是参考实现） | 使用方接入配置中心；example 演示文件实现                         |
| `GarfieldSerializer`            | `JacksonSerializer`                       | 全项目对象 ↔ byte[]/String 序列化                        |


---

## 引入方式


| 方式        | 说明                                                         |
| --------- | ---------------------------------------------------------- |
| **开箱即用**  | 引入 `garfield-spring-boot-starter`，一个依赖搞定                   |
| **最精简**   | 引入 `common` + `engine` + `transfer` + `process`，自行实现全部 SPI |
| **部分自定义** | 引入 `spring-boot-starter` + Maven exclusion 排除不需要的默认实现      |


---

## 技术栈


| 项           | 版本                                |
| ----------- | --------------------------------- |
| JDK         | 21                                |
| Spring Boot | 3.2.x（Starter 层依赖，核心模块 Spring 无关） |
| 配置格式        | JSON 文件 + JDK WatchService 热更新    |
| 构建工具        | Maven                             |
| 测试          | JUnit 5 + Mockito                 |
| 熔断（可选）      | Resilience4j                      |
| 分布式锁（可选）    | Redisson                          |


---

## 扩展自定义 StorageType

三步即可让 garfield 认识你的存储介质：

1. **定义 enum 实现 `StorageType`**
  ```java
   public enum MyStorageType implements StorageType { MY_CUSTOM_STORAGE }
  ```
2. **写一个 `StorageEngineFactory`**，`storageType()` 返回你的 enum 实例。Spring 会自动扫到。
3. **在配置文件中用名字引用**（大小写、首尾空白都会被规范化）：
  ```json
   { "storageType": "my_custom_storage", ... }
  ```

参考 `garfield-example/custom/` 下的 `MyStorageType` + `CustomDemoEngine` + `CustomDemoEngineFactory` 三件套。

### 接入配置中心

garfield 不默认装配 `ConfigLoader`——使用方必须自己提供 bean。example 用 `FileConfigLoader` 做参考实现。接入 Apollo/Nacos 等配置中心的伪代码：

```java
public class ApolloConfigLoader implements ConfigLoader {
    private final Config apollo;
    private final String key;
    private final ObjectMapper om;

    public ApolloConfigLoader(Config apollo, String key, StorageTypeRegistry registry) {
        this.apollo = apollo;
        this.key = key;
        this.om = GarfieldObjectMappers.create(registry);   // 规范化规则由框架下发
    }

    @Override public StorageConfig load() {
        return om.readValue(apollo.getProperty(key, "{}"), StorageConfig.class);
    }

    @Override public void watch(Consumer<StorageConfig> cb) {
        apollo.addChangeListener(c -> { if (c.isChanged(key)) cb.accept(load()); });
    }
}
```

（实际代码在使用方仓库中，不入 garfield。）

---

## License

Apache License 2.0