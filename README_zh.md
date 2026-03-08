<div align="center">

# dag-flow

**基于 DAG 的 Java 并行计算框架**

声明任务依赖关系，框架自动最大化并行度，简化多线程任务编排开发。

[![Java](https://img.shields.io/badge/Java-21%2B-blue?logo=openjdk)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10-02303A?logo=gradle)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green)](LICENSE)

[**English**](README.md) | [**中文**](README_zh.md)

</div>

---

## 特性

- **基于 DAG 的并行执行** — 基于 `CompletableFuture`，根据声明的依赖关系自动最大化并行度
- **多种命令类型** — `SyncCommand`（调用线程）、`AsyncCommand`（I/O 线程池）、`CalcCommand`（CPU 线程池）、`BatchCommand`（扇出，支持 ALL/ANY/AT_LEAST_N 策略）
- **环路检测** — 执行前基于 DFS 的环路检测，清晰的错误报告
- **流式构建 API** — 链式调用 `.node().depend()`；Builder 可跨多次运行复用
- **自动命名** — `node(Class)` 自动生成名称（`fetchOrder#0`、`fetchOrder#1`、...）；`node(Function)` 使用 `node#0`、`node#1`、...
- **Lambda 支持** — `node()` 接受 `Function<C, R>` 或 `Consumer<C>`，轻量级节点无需建类
- **可扩展架构** — `JobBuilder` 支持继承扩展，方便接入第三方容错框架
- **Hystrix 集成** — `dag-flow-hystrix` 模块将 Netflix `HystrixCommand` 包装为 DAG 节点
- **Resilience4j 集成** — `dag-flow-resilience4j` 模块提供熔断器、重试、隔离仓、限流器、超时控制等能力
- **Spring Boot Starter** — `dag-flow-spring-boot-starter` 自动配置 dag-flow 引擎，`dependSpringBean()` 开箱即用
- **虚拟线程** — `useVirtualThreads()` 启用 Java 21 虚拟线程，所有非 SyncCommand 节点在虚拟线程上执行；传统线程池作为默认模式保留
- **智能线程池** — I/O 池（2x–8x 核心数）和 CPU 池（核心数+1），拒绝策略为 `CallerRunsPolicy`
- **OpenTelemetry 链路追踪** — 内置 `opentelemetry-api` 分布式追踪；每次 DAG 执行创建根 Span，每个节点创建子 Span，批量子任务各自创建 Span — 无 SDK 时为 no-op，零开销
- **错误传播** — 节点异常以 `ExecutionException` 传播，下游节点自动取消

## 模块结构

| 模块 | 说明 |
|---|---|
| `dag-flow-core` | 核心框架：DAG 构建器、运行器、命令 API、线程池 |
| `dag-flow-hystrix` | Netflix Hystrix 扩展 |
| `dag-flow-resilience4j` | Resilience4j 扩展（熔断器、重试、隔离仓、限流器、超时控制） |
| `dag-flow-spring-boot-starter` | Spring Boot 4 自动配置 Starter |

## 快速开始

### 安装

在 `build.gradle` 中添加依赖：

```groovy
dependencies {
    // 核心模块（必选）
    implementation 'com.lesofn:dag-flow-core:1.0-SNAPSHOT'

    // Spring Boot Starter（可选 — 在 Spring Boot 应用中自动配置 dag-flow）
    implementation 'com.lesofn:dag-flow-spring-boot-starter:1.0-SNAPSHOT'

    // Hystrix 扩展（可选）
    implementation 'com.lesofn:dag-flow-hystrix:1.0-SNAPSHOT'

    // Resilience4j 扩展（可选）
    implementation 'com.lesofn:dag-flow-resilience4j:1.0-SNAPSHOT'
}
```

### 1. 定义 Context

Context 承载请求数据，并提供对上游结果的访问：

```java
public class OrderContext extends DagFlowContext {
    private String orderId;
    // getters & setters
}
```

### 2. 定义命令节点

```java
// 异步 I/O 节点（运行在异步线程池）
public class FetchOrder implements AsyncCommand<OrderContext, Order> {
    @Override
    public Order run(OrderContext context) {
        return orderService.getById(context.getOrderId());
    }
}

// CPU 密集型节点（运行在计算线程池）
public class CalcDiscount implements CalcCommand<OrderContext, BigDecimal> {
    @Override
    public BigDecimal run(OrderContext context) {
        Order order = context.getResult(FetchOrder.class);
        return discountEngine.calculate(order);
    }
}
```

### 3. 构建并运行 DAG

```java
OrderContext context = new OrderContext();
context.setOrderId("12345");

JobRunner<OrderContext> runner = new JobBuilder<OrderContext>()
        .node(FetchOrder.class)
        .node(FetchUser.class)
        .node(CalcDiscount.class).depend(FetchOrder.class, FetchUser.class)
        .node(BuildResult.class).depend(CalcDiscount.class)
        .run(context);

Result result = runner.getResult(BuildResult.class);
```

以上代码构建并执行如下 DAG：

```
FetchOrder   FetchUser      ← 并行执行（无相互依赖）
        \     /
      CalcDiscount           ← 等待两者完成
           |
       BuildResult
```

## 架构

### 命令层级

```
DagFlowCommand<C, R>                  // 基础接口: R run(C context)
├── SyncCommand<C, R>                  // 在调用线程执行
├── AsyncCommand<C, R>                 // 在 I/O 线程池执行
│   └── BatchCommand<C, P, R>          // 按参数扇出 → Map<P, R>（ALL/ANY/atLeast）
└── CalcCommand<C, R>                  // 在 CPU 线程池执行
        ├── FunctionCommand            // Lambda Function<C, R> 包装
        └── ConsumerCommand            // Lambda Consumer<C> 包装

扩展模块（基于 SyncCommand）：
├── HystrixCommandWrapper              // dag-flow-hystrix: Netflix Hystrix 适配器
└── Resilience4jCommand                // dag-flow-resilience4j: Resilience4j 装饰器包装
```

### 核心组件

| 组件 | 说明 |
|---|---|
| `JobBuilder<C>` | 流式 API，用于 DAG 构建和节点注册（支持继承扩展） |
| `JobRunner<C>` | 基于 `CompletableFuture` 的执行引擎，支持结果获取 |
| `DagFlowContext` | 抽象 Context — 子类化以承载请求数据和访问上游结果 |
| `DagNode` | 运行时节点，包装命令及其 Future 和依赖关系 |
| `DagNodeCheck` | 基于 DFS 的环路检测，在执行前运行 |
| `DagFlowDefaultExecutor` | 异步和计算节点的默认线程池配置 |
| `DagFlowTracing` | OpenTelemetry 链路追踪工具 — 根 Span、节点 Span、批量子项 Span |

### 默认线程池

| 线程池 | 核心线程数 | 最大线程数 | 队列容量 | 适用场景 |
|---|---|---|---|---|
| **Async (I/O)** | CPU × 2 | CPU × 8 | CPU × 16 | 网络调用、数据库查询、文件 I/O |
| **Calc (CPU)** | CPU + 1 | CPU + 1 | CPU × 4 | 计算、转换、聚合 |

两个线程池均使用 `CallerRunsPolicy` 作为拒绝策略。

## 进阶用法

### 自动命名

使用 `node(Class)` 时无需指定名称，dag-flow 自动生成 `className#0`、`className#1`、... 形式的名称：

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class)       // 自动命名 "fetchOrder#0"
        .node(FetchOrder.class)       // 自动命名 "fetchOrder#1"
        .node(CalcDiscount.class)     // 自动命名 "calcDiscount#0"
        .run(context);
```

Lambda 节点使用 `node` 前缀：`node#0`、`node#1`、...

也可以显式指定名称：

```java
builder.node("myCustomName", FetchOrder.class);
```

### Lambda 节点

轻量级逻辑无需创建类：

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class)
        .node("format", (Function<OrderContext, String>) ctx -> {
            Order order = ctx.getResult(FetchOrder.class);
            return order.toString();
        }).depend(FetchOrder.class)
        .run(context);
```

### 批量命令

将一组参数扇出为并行子任务：

```java
public class BatchFetch implements BatchCommand<MyContext, Long, String> {
    @Override
    public Set<Long> batchParam(MyContext context) {
        return Set.of(1L, 2L, 3L);
    }

    @Override
    public String run(MyContext context, Long param) {
        return fetchById(param);
    }
}

// 结果为 Map<Long, String>
Map<Long, String> results = runner.getResult(BatchFetch.class);
```

#### 批量执行策略

默认情况下，`BatchCommand` 等待**所有**子任务完成。覆盖 `batchStrategy()` 可改变此行为：

| 策略 | 说明 |
|---|---|
| `BatchStrategy.ALL` | 等待所有子任务完成（默认） |
| `BatchStrategy.ANY` | **1** 个子任务完成即返回，取消其余 |
| `BatchStrategy.atLeast(n)` | **n** 个子任务完成即返回，取消其余 |

```java
public class FastBatchFetch implements BatchCommand<MyContext, Long, String> {
    @Override
    public Set<Long> batchParam(MyContext context) {
        return Set.of(1L, 2L, 3L, 4L, 5L);
    }

    @Override
    public String run(MyContext context, Long param) {
        return fetchFromReplica(param);
    }

    @Override
    public BatchStrategy batchStrategy() {
        return BatchStrategy.ANY;             // 最先完成的结果胜出
        // return BatchStrategy.atLeast(3);   // 等待至少 3 个完成
    }
}
```

### 类内依赖声明

命令可以自行声明依赖关系：

```java
public class CalcDiscount implements CalcCommand<OrderContext, BigDecimal> {
    @Override
    public Class<? extends DagFlowCommand<OrderContext, ?>> dependNode() {
        return FetchOrder.class;
    }

    @Override
    public BigDecimal run(OrderContext context) { ... }
}

// Builder 中无需调用 .depend()
new JobBuilder<OrderContext>()
        .node(CalcDiscount.class)      // 自动解析对 FetchOrder 的依赖
        .run(context);
```

### Hystrix 集成

使用 `dag-flow-hystrix` 模块包装现有的 `HystrixCommand` 实现：

```java
// 添加依赖: implementation 'com.lesofn:dag-flow-hystrix:1.0-SNAPSHOT'

JobRunner<MyContext> runner = new HystrixJobBuilder<MyContext>()
        .addHystrixNode(MyHystrixCommand.class)
        .run(context);

String result = runner.getResult("myHystrixCommand");
// 或使用类型安全的辅助方法：
String result = HystrixJobBuilder.getHystrixResult(runner, MyHystrixCommand.class);
```

### Resilience4j 集成

使用 `dag-flow-resilience4j` 模块为 DAG 节点添加容错能力：

```java
// 添加依赖: implementation 'com.lesofn:dag-flow-resilience4j:1.0-SNAPSHOT'

CircuitBreaker cb = CircuitBreaker.of("myService", CircuitBreakerConfig.ofDefaults());
Retry retry = Retry.of("myService", RetryConfig.custom().maxAttempts(3).build());

Resilience4jCommand<MyContext, String> command =
        new Resilience4jCommand<>(ctx -> callRemoteService(ctx))
                .withCircuitBreaker(cb)
                .withRetry(retry);

JobRunner<MyContext> runner = new Resilience4jJobBuilder<MyContext>()
        .addResilience4jNode("protectedCall", command)
        .node(DownstreamJob.class).depend("protectedCall")
        .run(context);
```

支持的装饰器：`CircuitBreaker`（熔断器）、`Retry`（重试）、`Bulkhead`（隔离仓）、`RateLimiter`（限流器）、`TimeLimiter`（超时控制）— 可自由组合。

### Spring Boot Starter

添加 `dag-flow-spring-boot-starter` 即可在 Spring Boot 应用中自动配置 dag-flow 引擎：

```groovy
// build.gradle
implementation 'com.lesofn:dag-flow-spring-boot-starter:1.0-SNAPSHOT'
```

安装后，`SpringContextHolder` 自动注册，`dependSpringBean()` 开箱即用，无需手动配置：

```java
// 实现 DagFlowCommand 的 Spring Bean 可直接用作 DAG 节点
@Component
public class OrderService implements AsyncCommand<OrderContext, Order> {
    @Autowired
    private OrderRepository orderRepository;

    @Override
    public Order run(OrderContext context) {
        return orderRepository.findById(context.getOrderId());
    }
}

// 在 DAG 构建中通过名称引用 Spring Bean
new JobBuilder<OrderContext>()
        .node(CalcDiscount.class)
        .dependSpringBean("orderService")   // 从 Spring ApplicationContext 中解析
        .run(context);
```

通过 `application.properties` / `application.yml` 配置：

```properties
# 禁用 dag-flow 自动配置（默认：true）
dagflow.enabled=false
```

### 虚拟线程 (Java 21+)

一行代码启用虚拟线程：

```java
JobRunner<MyContext> runner = new JobBuilder<MyContext>()
        .useVirtualThreads()                   // 启用虚拟线程
        .node(FetchOrder.class)                // AsyncCommand → 虚拟线程
        .node(FetchUser.class)                 // AsyncCommand → 虚拟线程
        .node(CalcDiscount.class).depend(FetchOrder.class, FetchUser.class)
        .run(context);
```

- `SyncCommand` — 仍在调用线程上执行（不变）
- `AsyncCommand` / `CalcCommand` — 在虚拟线程上执行，替代传统线程池
- 不调用 `useVirtualThreads()` 时，使用传统 I/O 和 CPU 线程池（默认行为）

### OpenTelemetry 链路追踪

dag-flow 自动为每次 DAG 执行创建分布式追踪 Span。当 classpath 中包含 `opentelemetry-api` 时：

- 每次 DAG 执行创建**根 Span**（`dagflow.run`），包含 `dagflow.node.count` 属性
- 每个**节点**创建子 Span（`dagflow.node.<name>`），包含 `dagflow.node.name` 和 `dagflow.node.type` 属性
- 每个**批量子任务**创建独立子 Span（`dagflow.batch.<name>`），包含 `dagflow.batch.param` 属性

未配置 OpenTelemetry SDK 时，所有追踪操作均为 **no-op**，零性能开销。

```java
// 方式一：使用 GlobalOpenTelemetry（默认 — 零配置）
// 按常规方式全局配置 OpenTelemetry SDK 即可

// 方式二：提供自定义实例
DagFlowTracing.setOpenTelemetry(myOpenTelemetrySdk);

// 正常运行 DAG — Span 自动创建
new JobBuilder<MyContext>()
        .node(FetchOrder.class)
        .node(CalcDiscount.class).depend(FetchOrder.class)
        .run(context);

// 重置为 GlobalOpenTelemetry
DagFlowTracing.setOpenTelemetry(null);
```

Spring Boot 配置：

```properties
# 禁用链路追踪（默认：true）
dagflow.tracing-enabled=false
```

### 自定义线程池

为特定节点覆盖默认线程池：

```java
// 在命令类中逐节点覆盖
public class CustomJob implements AsyncCommand<MyContext, String> {
    @Override
    public Executor executor() {
        return myCustomExecutor;
    }
    // ...
}

// 或为 Lambda 节点传入线程池
builder.node("custom", myFunction, myExecutor);
```

## 项目结构

```
dag-flow/
├── dag-flow-core/                           # 核心模块
│   └── src/main/java/com/lesofn/dagflow/
│       ├── JobBuilder.java                  # 流式 DAG 构建（支持扩展）
│       ├── JobRunner.java                   # CompletableFuture 执行引擎
│       ├── api/
│       │   ├── DagFlowCommand.java          # 基础命令接口
│       │   ├── SyncCommand.java             # 同步命令
│       │   ├── AsyncCommand.java            # 异步 (I/O) 命令
│       │   ├── CalcCommand.java             # CPU 密集型命令
│       │   ├── BatchCommand.java            # 批量扇出命令
│       │   ├── BatchStrategy.java           # 批量策略：ALL / ANY / atLeast(N)
│       │   ├── context/                     # Context 和注入接口
│       │   ├── depend/                      # 依赖声明接口
│       │   └── function/                    # Lambda 包装器
│       ├── exception/                       # DagFlowBuildException, CycleException 等
│       ├── executor/                        # 默认线程池配置
│       ├── model/                           # DagNode, DagNodeCheck, DagNodeFactory
│       ├── tracing/                         # OpenTelemetry 链路追踪 (DagFlowTracing)
│       └── spring/                          # 可选的 Spring 集成
├── dag-flow-hystrix/                        # Hystrix 扩展模块
│   └── src/main/java/com/lesofn/dagflow/hystrix/
│       ├── HystrixCommandWrapper.java       # HystrixCommand → SyncCommand 适配器
│       └── HystrixJobBuilder.java           # 提供 addHystrixNode() 的构建器
├── dag-flow-resilience4j/                   # Resilience4j 扩展模块
│   └── src/main/java/com/lesofn/dagflow/resilience4j/
│       ├── Resilience4jCommand.java         # Resilience4j 装饰器包装
│       └── Resilience4jJobBuilder.java      # 提供 addResilience4jNode() 的构建器
└── dag-flow-spring-boot-starter/            # Spring Boot 4 自动配置 Starter
    └── src/main/java/com/lesofn/dagflow/spring/boot/autoconfigure/
        ├── DagFlowAutoConfiguration.java    # 自动注册 SpringContextHolder
        └── DagFlowProperties.java           # dagflow.enabled 配置
```

## 性能基准测试

传统线程池 vs 虚拟线程对比（Java 21, 8 vCPU）：

| 场景 | 传统线程池 | 虚拟线程 | 加速比 |
|---|---|---|---|
| 10 个并行 I/O 节点（每个 100ms） | 200.97 ms | 101.24 ms | **1.99x** |
| 50 个并行 I/O 节点（每个 50ms） | 302.88 ms | 58.43 ms | **5.18x** |
| 100 个并行 I/O 节点（每个 20ms） | 222.91 ms | 26.19 ms | **8.51x** |
| 8 个并行 CPU 节点 | 4.65 ms | 4.47 ms | 1.04x |
| 混合 DAG（5 I/O → CPU → I/O） | 102.71 ms | 102.53 ms | 1.00x |
| 多层 DAG（3 层 × 10 节点，30ms） | 121.80 ms | 62.03 ms | **1.96x** |

**关键结论：**
- 虚拟线程在 **I/O 密集型** 场景表现突出 — 100 个并发阻塞节点时最高 **8.5 倍**提速
- 对于 **CPU 密集型** 任务，性能基本持平（虚拟线程几乎无额外开销）
- 并发 I/O 节点越多，虚拟线程相比固定大小线程池的优势越明显

自行运行基准测试：

```bash
./gradlew :dag-flow-core:benchmark    # 仅运行基准测试（不包含在默认 test 中）
```

## 构建与测试

```bash
./gradlew build                                 # 构建所有模块
./gradlew test                                  # 运行所有测试（Spock + JUnit Platform）
./gradlew :dag-flow-core:test                  # 仅运行核心模块测试
./gradlew :dag-flow-hystrix:test               # 仅运行 Hystrix 测试
./gradlew :dag-flow-resilience4j:test          # 仅运行 Resilience4j 测试
./gradlew :dag-flow-spring-boot-starter:test   # 仅运行 Spring Boot Starter 测试
./gradlew :dag-flow-core:benchmark             # 运行性能基准测试
./gradlew clean build                           # 清理后构建
```

## 环境要求

- **Java** 21+
- **Gradle** 8.10+（已包含 Wrapper）

## 许可证

[Apache License 2.0](LICENSE)
