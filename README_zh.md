<div align="center">

# dag-flow

**基于 DAG 的 Java 并行计算框架**

声明任务依赖关系，框架自动最大化并行度，简化多线程任务编排开发。

[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10-02303A?logo=gradle)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green)](LICENSE)

[**English**](README.md) | [**中文**](README_zh.md)

</div>

---

## 特性

- **基于 DAG 的并行执行** — 基于 `CompletableFuture`，根据声明的依赖关系自动最大化并行度
- **多种命令类型** — `SyncCommand`（调用线程）、`AsyncCommand`（I/O 线程池）、`CalcCommand`（CPU 线程池）、`BatchCommand`（扇出）
- **环路检测** — 执行前基于 DFS 的环路检测，清晰的错误报告
- **流式构建 API** — 链式调用 `.addNode().depend()`；Builder 可跨多次运行复用
- **Lambda 支持** — `funcNode()` 接受 `Function<C, R>` 或 `Consumer<C>`，轻量级节点无需建类
- **可扩展架构** — `JobBuilder` 支持继承扩展，方便接入第三方容错框架
- **Hystrix 集成** — `dag-flow-hystrix` 模块将 Netflix `HystrixCommand` 包装为 DAG 节点
- **Resilience4j 集成** — `dag-flow-resilience4j` 模块提供熔断器、重试、隔离仓、限流器、超时控制等能力
- **Spring 集成** — 可选；通过 `dependSpringBean()` 将 Spring Bean 作为 DAG 节点
- **智能线程池** — I/O 池（2x–8x 核心数）和 CPU 池（核心数+1），拒绝策略为 `CallerRunsPolicy`
- **错误传播** — 节点异常以 `ExecutionException` 传播，下游节点自动取消

## 模块结构

| 模块 | 说明 |
|---|---|
| `dag-flow-core` | 核心框架：DAG 构建器、运行器、命令 API、线程池 |
| `dag-flow-hystrix` | Netflix Hystrix 扩展 |
| `dag-flow-resilience4j` | Resilience4j 扩展（熔断器、重试、隔离仓、限流器、超时控制） |

## 快速开始

### 安装

在 `build.gradle` 中添加依赖：

```groovy
dependencies {
    // 核心模块（必选）
    implementation 'com.lesofn:dag-flow-core:1.0-SNAPSHOT'

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
        .addNode(FetchOrder.class)
        .addNode(FetchUser.class)
        .addNode(CalcDiscount.class).depend(FetchOrder.class, FetchUser.class)
        .addNode(BuildResult.class).depend(CalcDiscount.class)
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
│   └── BatchCommand<C, P, R>          // 按参数扇出 → Map<P, R>
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

### 默认线程池

| 线程池 | 核心线程数 | 最大线程数 | 队列容量 | 适用场景 |
|---|---|---|---|---|
| **Async (I/O)** | CPU × 2 | CPU × 8 | CPU × 16 | 网络调用、数据库查询、文件 I/O |
| **Calc (CPU)** | CPU + 1 | CPU + 1 | CPU × 4 | 计算、转换、聚合 |

两个线程池均使用 `CallerRunsPolicy` 作为拒绝策略。

## 进阶用法

### Lambda 节点

轻量级逻辑无需创建类：

```java
new JobBuilder<OrderContext>()
        .addNode(FetchOrder.class)
        .funcNode("format", (Function<OrderContext, String>) ctx -> {
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
        .addNode(CalcDiscount.class)   // 自动解析对 FetchOrder 的依赖
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
        .addNode(DownstreamJob.class).depend("protectedCall")
        .run(context);
```

支持的装饰器：`CircuitBreaker`（熔断器）、`Retry`（重试）、`Bulkhead`（隔离仓）、`RateLimiter`（限流器）、`TimeLimiter`（超时控制）— 可自由组合。

### Spring 集成

将 Spring Bean 作为 DAG 节点（需要 classpath 中有 `spring-context`）：

```java
new JobBuilder<MyContext>()
        .addNode(MyService.class)
        .dependSpringBean("anotherService")
        .run(context);
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
builder.funcNode("custom", myFunction, myExecutor);
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
│       │   ├── context/                     # Context 和注入接口
│       │   ├── depend/                      # 依赖声明接口
│       │   └── function/                    # Lambda 包装器
│       ├── exception/                       # DagFlowBuildException, CycleException 等
│       ├── executor/                        # 默认线程池配置
│       ├── model/                           # DagNode, DagNodeCheck, DagNodeFactory
│       └── spring/                          # 可选的 Spring 集成
├── dag-flow-hystrix/                        # Hystrix 扩展模块
│   └── src/main/java/com/lesofn/dagflow/hystrix/
│       ├── HystrixCommandWrapper.java       # HystrixCommand → SyncCommand 适配器
│       └── HystrixJobBuilder.java           # 提供 addHystrixNode() 的构建器
└── dag-flow-resilience4j/                   # Resilience4j 扩展模块
    └── src/main/java/com/lesofn/dagflow/resilience4j/
        ├── Resilience4jCommand.java         # Resilience4j 装饰器包装
        └── Resilience4jJobBuilder.java      # 提供 addResilience4jNode() 的构建器
```

## 构建与测试

```bash
./gradlew build                        # 构建所有模块
./gradlew test                         # 运行所有测试（Spock + JUnit Platform）
./gradlew :dag-flow-core:test         # 仅运行核心模块测试
./gradlew :dag-flow-hystrix:test      # 仅运行 Hystrix 测试
./gradlew :dag-flow-resilience4j:test # 仅运行 Resilience4j 测试
./gradlew clean build                  # 清理后构建
```

## 环境要求

- **Java** 17+
- **Gradle** 8.10+（已包含 Wrapper）

## 许可证

[Apache License 2.0](LICENSE)
