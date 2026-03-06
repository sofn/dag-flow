<div align="center">

# dag-flow

**A DAG-based parallel computation framework for Java**

Simplify multi-threaded task orchestration — declare dependencies, and the framework maximizes parallelism automatically.

[![Java](https://img.shields.io/badge/Java-21%2B-blue?logo=openjdk)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10-02303A?logo=gradle)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green)](LICENSE)

[**English**](README.md) | [**中文**](README_zh.md)

</div>

---

## Features

- **DAG-based parallel execution** — Automatically maximizes parallelism based on declared dependencies using `CompletableFuture`
- **Multiple command types** — `SyncCommand` (caller thread), `AsyncCommand` (I/O pool), `CalcCommand` (CPU pool), `BatchCommand` (fan-out)
- **Cycle detection** — DFS-based cycle detection before execution with clear error reporting
- **Fluent builder API** — Chain `.addNode().depend()` calls; builder is reusable across runs
- **Lambda support** — `funcNode()` accepts `Function<C, R>` or `Consumer<C>` for lightweight nodes
- **Extensible architecture** — `JobBuilder` is designed for extension; create custom builders for third-party integrations
- **Hystrix integration** — `dag-flow-hystrix` module wraps Netflix `HystrixCommand` into the DAG
- **Resilience4j integration** — `dag-flow-resilience4j` module provides CircuitBreaker, Retry, Bulkhead, RateLimiter, TimeLimiter support
- **Spring Boot Starter** — `dag-flow-spring-boot-starter` auto-configures dag-flow engine; `dependSpringBean()` works out of the box
- **Virtual threads** — `useVirtualThreads()` enables Java 21 virtual threads for all non-sync nodes; traditional thread pools remain the default
- **Smart thread pools** — I/O pool (2x–8x cores) and CPU pool (cores+1) with `CallerRunsPolicy`
- **Error propagation** — Node exceptions propagate as `ExecutionException`; downstream nodes are cancelled

## Module Structure

| Module | Description |
|---|---|
| `dag-flow-core` | Core framework: DAG builder, runner, command API, thread pools |
| `dag-flow-hystrix` | Netflix Hystrix extension |
| `dag-flow-resilience4j` | Resilience4j extension (CircuitBreaker, Retry, Bulkhead, RateLimiter, TimeLimiter) |
| `dag-flow-spring-boot-starter` | Spring Boot 4 auto-configuration starter |

## Quick Start

### Installation

Add to your `build.gradle`:

```groovy
dependencies {
    // Core (required)
    implementation 'com.lesofn:dag-flow-core:1.0-SNAPSHOT'

    // Spring Boot Starter (optional — auto-configures dag-flow in Spring Boot apps)
    implementation 'com.lesofn:dag-flow-spring-boot-starter:1.0-SNAPSHOT'

    // Hystrix extension (optional)
    implementation 'com.lesofn:dag-flow-hystrix:1.0-SNAPSHOT'

    // Resilience4j extension (optional)
    implementation 'com.lesofn:dag-flow-resilience4j:1.0-SNAPSHOT'
}
```

### 1. Define a Context

The context carries request data and provides access to upstream results:

```java
public class OrderContext extends DagFlowContext {
    private String orderId;
    // getters & setters
}
```

### 2. Define Command Nodes

```java
// Async I/O node (runs on async thread pool)
public class FetchOrder implements AsyncCommand<OrderContext, Order> {
    @Override
    public Order run(OrderContext context) {
        return orderService.getById(context.getOrderId());
    }
}

// CPU-bound node (runs on calc thread pool)
public class CalcDiscount implements CalcCommand<OrderContext, BigDecimal> {
    @Override
    public BigDecimal run(OrderContext context) {
        Order order = context.getResult(FetchOrder.class);
        return discountEngine.calculate(order);
    }
}
```

### 3. Build and Run the DAG

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

This builds and executes the following DAG:

```
FetchOrder   FetchUser      ← parallel (no mutual dependency)
        \     /
      CalcDiscount           ← waits for both
           |
       BuildResult
```

## Architecture

### Command Hierarchy

```
DagFlowCommand<C, R>                  // Base: R run(C context)
├── SyncCommand<C, R>                  // Runs on caller thread
├── AsyncCommand<C, R>                 // Runs on I/O thread pool
│   └── BatchCommand<C, P, R>          // Fan-out per param → Map<P, R>
└── CalcCommand<C, R>                  // Runs on CPU thread pool
        ├── FunctionCommand            // Lambda Function<C, R> wrapper
        └── ConsumerCommand            // Lambda Consumer<C> wrapper

Extensions (SyncCommand-based):
├── HystrixCommandWrapper              // dag-flow-hystrix: Netflix Hystrix adapter
└── Resilience4jCommand                // dag-flow-resilience4j: Resilience4j decorator wrapper
```

### Core Components

| Component | Description |
|---|---|
| `JobBuilder<C>` | Fluent API for DAG construction and node registration (extensible) |
| `JobRunner<C>` | `CompletableFuture`-based execution engine with result retrieval |
| `DagFlowContext` | Abstract context — subclass to carry request data and access upstream results |
| `DagNode` | Runtime node wrapping a command with its future and dependencies |
| `DagNodeCheck` | DFS-based cycle detection, runs before execution |
| `DagFlowDefaultExecutor` | Default thread pool configuration for async and calc nodes |

### Default Thread Pools

| Pool | Core | Max | Queue | Use Case |
|---|---|---|---|---|
| **Async (I/O)** | CPU × 2 | CPU × 8 | CPU × 16 | Network calls, DB queries, file I/O |
| **Calc (CPU)** | CPU + 1 | CPU + 1 | CPU × 4 | Computation, transformation, aggregation |

Both pools use `CallerRunsPolicy` as the rejection handler.

## Advanced Usage

### Lambda Nodes

For lightweight logic, skip creating a class:

```java
new JobBuilder<OrderContext>()
        .addNode(FetchOrder.class)
        .funcNode("format", (Function<OrderContext, String>) ctx -> {
            Order order = ctx.getResult(FetchOrder.class);
            return order.toString();
        }).depend(FetchOrder.class)
        .run(context);
```

### Batch Command

Fan-out a set of parameters into parallel sub-tasks:

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

// Result is Map<Long, String>
Map<Long, String> results = runner.getResult(BatchFetch.class);
```

### In-Class Dependency Declaration

Commands can declare their own dependencies:

```java
public class CalcDiscount implements CalcCommand<OrderContext, BigDecimal> {
    @Override
    public Class<? extends DagFlowCommand<OrderContext, ?>> dependNode() {
        return FetchOrder.class;
    }

    @Override
    public BigDecimal run(OrderContext context) { ... }
}

// No need to call .depend() in the builder
new JobBuilder<OrderContext>()
        .addNode(CalcDiscount.class)   // auto-resolves dependency on FetchOrder
        .run(context);
```

### Hystrix Integration

Use `dag-flow-hystrix` module to wrap existing `HystrixCommand` implementations:

```java
// Add dependency: implementation 'com.lesofn:dag-flow-hystrix:1.0-SNAPSHOT'

JobRunner<MyContext> runner = new HystrixJobBuilder<MyContext>()
        .addHystrixNode(MyHystrixCommand.class)
        .run(context);

String result = runner.getResult("myHystrixCommand");
// Or use the type-safe helper:
String result = HystrixJobBuilder.getHystrixResult(runner, MyHystrixCommand.class);
```

### Resilience4j Integration

Use `dag-flow-resilience4j` module to add fault tolerance to DAG nodes:

```java
// Add dependency: implementation 'com.lesofn:dag-flow-resilience4j:1.0-SNAPSHOT'

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

Supported decorators: `CircuitBreaker`, `Retry`, `Bulkhead`, `RateLimiter`, `TimeLimiter` — can be combined freely.

### Spring Boot Starter

Add `dag-flow-spring-boot-starter` to auto-configure the dag-flow engine in Spring Boot applications:

```groovy
// build.gradle
implementation 'com.lesofn:dag-flow-spring-boot-starter:1.0-SNAPSHOT'
```

Once installed, `SpringContextHolder` is auto-registered and `dependSpringBean()` works out of the box — no manual configuration needed:

```java
// Spring beans that implement DagFlowCommand can be used as DAG nodes
@Component
public class OrderService implements AsyncCommand<OrderContext, Order> {
    @Autowired
    private OrderRepository orderRepository;

    @Override
    public Order run(OrderContext context) {
        return orderRepository.findById(context.getOrderId());
    }
}

// Reference Spring beans by name in DAG construction
new JobBuilder<OrderContext>()
        .addNode(CalcDiscount.class)
        .dependSpringBean("orderService")   // resolved from Spring ApplicationContext
        .run(context);
```

Configuration via `application.properties` / `application.yml`:

```properties
# Disable dag-flow auto-configuration (default: true)
dagflow.enabled=false
```

### Virtual Threads (Java 21+)

Enable virtual threads for all non-sync nodes with a single call:

```java
JobRunner<MyContext> runner = new JobBuilder<MyContext>()
        .useVirtualThreads()                   // enable virtual threads
        .addNode(FetchOrder.class)             // AsyncCommand → virtual thread
        .addNode(FetchUser.class)              // AsyncCommand → virtual thread
        .addNode(CalcDiscount.class).depend(FetchOrder.class, FetchUser.class)
        .run(context);
```

- `SyncCommand` — still runs on the caller thread (unchanged)
- `AsyncCommand` / `CalcCommand` — runs on virtual threads instead of platform thread pools
- Without `useVirtualThreads()`, the traditional I/O and CPU thread pools are used (default behavior)

### Custom Executor

Override the default thread pool for specific nodes:

```java
// Per-node override in the command class
public class CustomJob implements AsyncCommand<MyContext, String> {
    @Override
    public Executor executor() {
        return myCustomExecutor;
    }
    // ...
}

// Or pass executor to lambda nodes
builder.funcNode("custom", myFunction, myExecutor);
```

## Project Structure

```
dag-flow/
├── dag-flow-core/                           # Core module
│   └── src/main/java/com/lesofn/dagflow/
│       ├── JobBuilder.java                  # Fluent DAG construction (extensible)
│       ├── JobRunner.java                   # CompletableFuture execution engine
│       ├── api/
│       │   ├── DagFlowCommand.java          # Base command interface
│       │   ├── SyncCommand.java             # Synchronous command
│       │   ├── AsyncCommand.java            # Async (I/O) command
│       │   ├── CalcCommand.java             # CPU-bound command
│       │   ├── BatchCommand.java            # Batch fan-out command
│       │   ├── context/                     # Context & injection interfaces
│       │   ├── depend/                      # Dependency declaration interface
│       │   └── function/                    # Lambda wrappers
│       ├── exception/                       # DagFlowBuildException, CycleException, etc.
│       ├── executor/                        # Default thread pool configuration
│       ├── model/                           # DagNode, DagNodeCheck, DagNodeFactory
│       └── spring/                          # Optional Spring integration
├── dag-flow-hystrix/                        # Hystrix extension module
│   └── src/main/java/com/lesofn/dagflow/hystrix/
│       ├── HystrixCommandWrapper.java       # HystrixCommand → SyncCommand adapter
│       └── HystrixJobBuilder.java           # Builder with addHystrixNode()
├── dag-flow-resilience4j/                   # Resilience4j extension module
│   └── src/main/java/com/lesofn/dagflow/resilience4j/
│       ├── Resilience4jCommand.java         # Resilience4j decorator wrapper
│       └── Resilience4jJobBuilder.java      # Builder with addResilience4jNode()
└── dag-flow-spring-boot-starter/            # Spring Boot 4 auto-configuration starter
    └── src/main/java/com/lesofn/dagflow/spring/boot/autoconfigure/
        ├── DagFlowAutoConfiguration.java    # Auto-registers SpringContextHolder
        └── DagFlowProperties.java           # dagflow.enabled configuration
```

## Performance Benchmark

Platform thread pool vs Virtual threads comparison (Java 21, 8 vCPU):

| Scenario | Platform Threads | Virtual Threads | Speedup |
|---|---|---|---|
| 10 Parallel I/O (100ms each) | 200.97 ms | 101.24 ms | **1.99x** |
| 50 Parallel I/O (50ms each) | 302.88 ms | 58.43 ms | **5.18x** |
| 100 Parallel I/O (20ms each) | 222.91 ms | 26.19 ms | **8.51x** |
| 8 Parallel CPU Nodes | 4.65 ms | 4.47 ms | 1.04x |
| Mixed DAG (5 I/O → CPU → I/O) | 102.71 ms | 102.53 ms | 1.00x |
| Multi-Layer DAG (3×10, 30ms) | 121.80 ms | 62.03 ms | **1.96x** |

**Key takeaways:**
- Virtual threads excel at **I/O-bound** workloads — up to **8.5x faster** with 100 concurrent blocking nodes
- For **CPU-bound** tasks, performance is comparable (virtual threads add negligible overhead)
- The more concurrent I/O nodes, the greater the advantage of virtual threads over fixed-size thread pools

Run benchmarks yourself:

```bash
./gradlew :dag-flow-core:benchmark    # Benchmarks only (excluded from default test)
```

## Build & Test

```bash
./gradlew build                                 # Build all modules
./gradlew test                                  # Run all tests (Spock + JUnit Platform)
./gradlew :dag-flow-core:test                  # Run core tests only
./gradlew :dag-flow-hystrix:test               # Run Hystrix tests only
./gradlew :dag-flow-resilience4j:test          # Run Resilience4j tests only
./gradlew :dag-flow-spring-boot-starter:test   # Run Spring Boot Starter tests only
./gradlew :dag-flow-core:benchmark             # Run performance benchmarks
./gradlew clean build                           # Clean build
```

## Requirements

- **Java** 21+
- **Gradle** 8.10+ (wrapper included)

## License

[Apache License 2.0](LICENSE)
