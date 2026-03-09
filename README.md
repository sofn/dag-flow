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
- **Multiple command types** — `SyncCommand` (caller thread), `AsyncCommand` (I/O pool), `CalcCommand` (CPU pool), `BatchCommand` (fan-out with ALL/ANY/AT_LEAST_N strategies)
- **Cycle detection** — DFS-based cycle detection before execution with clear error reporting
- **Fluent builder API** — Chain `.node().depend()` calls; builder is reusable across runs
- **Auto-naming** — `node(Class)` auto-generates names (`fetchOrder#0`, `fetchOrder#1`, ...); `node(Function)` uses `node#0`, `node#1`, ...
- **Lambda support** — `node()` accepts `Function<C, R>` or `Consumer<C>` for lightweight nodes
- **Extensible architecture** — `JobBuilder` is designed for extension; create custom builders for third-party integrations
- **Hystrix integration** — `dag-flow-hystrix` module wraps Netflix `HystrixCommand` into the DAG
- **Resilience4j integration** — `dag-flow-resilience4j` module provides CircuitBreaker, Retry, Bulkhead, RateLimiter, TimeLimiter support
- **Spring Boot Starter** — `dag-flow-spring-boot-starter` auto-configures dag-flow engine; `dependSpringBean()` works out of the box
- **Virtual threads** — `useVirtualThreads()` enables Java 21 virtual threads for all non-sync nodes; traditional thread pools remain the default
- **Smart thread pools** — I/O pool (2x–8x cores) and CPU pool (cores+1) with `CallerRunsPolicy`
- **OpenTelemetry tracing** — Built-in distributed tracing via `opentelemetry-api`; creates root span per DAG run, child spans per node, and batch-item spans — no-op when SDK is absent
- **Replay profiling** — `enableReplay()` records per-node start/end timing; print text-based Gantt charts and waterfall timelines; Spring Boot web UI at `/dagflow/replay` with configurable LRU cache
- **Node timeout** — Per-node and DAG-level timeout support via `CompletableFuture.orTimeout()`; declare via builder `.timeout()` or command `timeout()` method
- **Lightweight retry** — Built-in per-node retry with configurable max attempts and delay — no external dependencies needed
- **Conditional execution** — `dependIf()` / `dependIfNot()` enable runtime branch selection; skip nodes based on upstream results
- **Fallback / default values** — Commands can define `fallback(context, cause)` to return degraded results on failure
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
        .node(FetchOrder.class)
        .node(FetchUser.class)
        .node(CalcDiscount.class).depend(FetchOrder.class, FetchUser.class)
        .node(BuildResult.class).depend(CalcDiscount.class)
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
│   └── BatchCommand<C, P, R>          // Fan-out per param → Map<P, R> (ALL/ANY/atLeast)
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
| `DagFlowTracing` | OpenTelemetry tracing utility — root, node, and batch-item spans |
| `DagFlowReplay` | Immutable execution record with per-node timing data |
| `DagFlowReplayPrinter` | Text-based Gantt chart and waterfall timeline renderer |

### Default Thread Pools

| Pool | Core | Max | Queue | Use Case |
|---|---|---|---|---|
| **Async (I/O)** | CPU × 2 | CPU × 8 | CPU × 16 | Network calls, DB queries, file I/O |
| **Calc (CPU)** | CPU + 1 | CPU + 1 | CPU × 4 | Computation, transformation, aggregation |

Both pools use `CallerRunsPolicy` as the rejection handler.

## Advanced Usage

### Auto-Naming

When you use `node(Class)` without an explicit name, dag-flow auto-generates a name using the pattern `className#0`, `className#1`, etc.:

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class)       // auto-named "fetchOrder#0"
        .node(FetchOrder.class)       // auto-named "fetchOrder#1"
        .node(CalcDiscount.class)     // auto-named "calcDiscount#0"
        .run(context);
```

Lambda nodes use the prefix `node`: `node#0`, `node#1`, ...

You can also provide an explicit name:

```java
builder.node("myCustomName", FetchOrder.class);
```

### Lambda Nodes

For lightweight logic, skip creating a class:

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class)
        .node("format", (Function<OrderContext, String>) ctx -> {
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

#### Batch Execution Strategies

By default, `BatchCommand` waits for **all** sub-tasks to complete. Override `batchStrategy()` to change this:

| Strategy | Description |
|---|---|
| `BatchStrategy.ALL` | Wait for all sub-tasks (default) |
| `BatchStrategy.ANY` | Return as soon as **1** sub-task completes; cancel the rest |
| `BatchStrategy.atLeast(n)` | Return as soon as **n** sub-tasks complete; cancel the rest |

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
        return BatchStrategy.ANY;             // first result wins
        // return BatchStrategy.atLeast(3);   // wait for at least 3
    }
}
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
        .node(CalcDiscount.class)      // auto-resolves dependency on FetchOrder
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
        .node(DownstreamJob.class).depend("protectedCall")
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
        .node(CalcDiscount.class)
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
        .node(FetchOrder.class)                // AsyncCommand → virtual thread
        .node(FetchUser.class)                 // AsyncCommand → virtual thread
        .node(CalcDiscount.class).depend(FetchOrder.class, FetchUser.class)
        .run(context);
```

- `SyncCommand` — still runs on the caller thread (unchanged)
- `AsyncCommand` / `CalcCommand` — runs on virtual threads instead of platform thread pools
- Without `useVirtualThreads()`, the traditional I/O and CPU thread pools are used (default behavior)

### OpenTelemetry Tracing

dag-flow automatically creates distributed tracing spans for every DAG execution. When `opentelemetry-api` is on the classpath:

- A **root span** (`dagflow.run`) is created per DAG execution with a `dagflow.node.count` attribute
- Each **node** gets a child span (`dagflow.node.<name>`) with `dagflow.node.name` and `dagflow.node.type` attributes
- Each **batch sub-task** gets its own child span (`dagflow.batch.<name>`) with the `dagflow.batch.param` attribute

When no OpenTelemetry SDK is configured, all tracing operations are **no-op** with zero overhead.

```java
// Option 1: Use GlobalOpenTelemetry (default — zero configuration needed)
// Just configure OpenTelemetry SDK globally as usual

// Option 2: Provide a custom instance
DagFlowTracing.setOpenTelemetry(myOpenTelemetrySdk);

// Then run the DAG as normal — spans are created automatically
new JobBuilder<MyContext>()
        .node(FetchOrder.class)
        .node(CalcDiscount.class).depend(FetchOrder.class)
        .run(context);

// Reset to GlobalOpenTelemetry
DagFlowTracing.setOpenTelemetry(null);
```

Spring Boot configuration:

```properties
# Disable tracing (default: true)
dagflow.tracing-enabled=false
```

### Replay Profiling

Enable execution profiling to record per-node timing data. After execution, print text-based Gantt charts or waterfall timelines:

```java
JobRunner<MyContext> runner = new JobBuilder<MyContext>()
        .enableReplay()                           // enable profiling
        .node(FetchOrder.class)
        .node(FetchUser.class)
        .node(CalcDiscount.class).depend(FetchOrder.class, FetchUser.class)
        .node(BuildResult.class).depend(CalcDiscount.class)
        .run(context);

// Get the replay record
DagFlowReplay replay = runner.getReplayRecord();

// Print text-based Gantt chart
System.out.println(replay.toGantt());

// Print Chrome DevTools-style waterfall timeline
System.out.println(replay.toTimeline());
```

Example Gantt chart output:

```
DAG Execution [4 nodes, 250ms, SUCCESS]  14:30:05.123
──────────────────────────────────────────────────────────────
FetchOrder   |████████████                                  |   0- 120ms [Async]
FetchUser    |██████                                        |   0-  60ms [Async]
CalcDiscount |            ██████████████                    | 120- 220ms [Calc]
BuildResult  |                          ████                | 220- 250ms [Sync]
──────────────────────────────────────────────────────────────
```

Example waterfall timeline output:

```
DAG Timeline [4 nodes, 250ms, SUCCESS]  14:30:05.123
──────────────────────────────────────────────────────────────
             0ms   50ms  100ms 150ms 200ms 250ms
             |     |     |     |     |     |
FetchOrder   ██████████████░░░░░░░░░░░░░░░░  120ms  Async  pool-1-thread-2
FetchUser    ████████░░░░░░░░░░░░░░░░░░░░░░   60ms  Async  pool-1-thread-3
CalcDiscount ░░░░░░░░░░░░██████████████░░░░  100ms  Calc   ForkJoin-1
BuildResult  ░░░░░░░░░░░░░░░░░░░░░░░░████░░   30ms  Sync   main
──────────────────────────────────────────────────────────────
```

**Spring Boot web UI:** When using `dag-flow-spring-boot-starter`, enable the replay web endpoint:

```properties
# application.properties
dagflow.replay-enabled=true
dagflow.replay-cache-size=100   # LRU cache: keep last N executions (default: 100)
```

Then visit `http://localhost:8080/dagflow/replay` for an interactive HTML waterfall visualization.

| Endpoint | Description |
|---|---|
| `GET /dagflow/replay` | HTML list of cached execution records |
| `GET /dagflow/replay/{id}` | HTML detail with waterfall chart |
| `GET /dagflow/replay/{id}/text` | Plain text Gantt + timeline |
| `GET /dagflow/replay/api` | JSON list |
| `GET /dagflow/replay/api/{id}` | JSON detail |

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
builder.node("custom", myFunction, myExecutor);
```

### Timeout Control

dag-flow supports both per-node and DAG-level timeouts to prevent blocking executions from hanging indefinitely.

#### Node-Level Timeout (Builder)

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class).timeout(Duration.ofSeconds(3))   // 3s timeout for this node
        .node(CalcDiscount.class).depend(FetchOrder.class)
        .run(context);
```

#### Node-Level Timeout (Command Declaration)

Commands can declare their own timeout by overriding the `timeout()` method:

```java
public class FetchOrder implements AsyncCommand<OrderContext, Order> {
    @Override
    public Duration timeout() {
        return Duration.ofSeconds(3);   // declare timeout in the command itself
    }

    @Override
    public Order run(OrderContext context) {
        return orderService.getById(context.getOrderId());
    }
}
```

Builder-set timeout takes priority over command-declared timeout.

#### DAG-Level Timeout

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class)
        .node(CalcDiscount.class).depend(FetchOrder.class)
        .dagTimeout(Duration.ofSeconds(10))   // entire DAG must finish in 10s
        .run(context);
```

When a timeout is exceeded, the node's `CompletableFuture` completes exceptionally with `TimeoutException`, which propagates as `ExecutionException`.

### Lightweight Retry

Built-in per-node retry in the core module — no external dependencies required:

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class).retry(3, Duration.ofMillis(100))   // 3 retries, 100ms delay
        .node(CalcDiscount.class).depend(FetchOrder.class)
        .run(context);
```

- On failure, the node retries up to `maxRetries` times with the specified delay between attempts
- After all retries are exhausted, the exception propagates (or fallback is invoked if configured)
- Retry is per-node; each node's retry policy is independent
- For batch commands, each batch item is retried independently

### Conditional Execution

Use `dependIf()` and `dependIfNot()` to create dynamic DAGs where branches are selected at runtime based on upstream results:

```java
new JobBuilder<OrderContext>()
        .node(CheckVip.class)
        .node(VipDiscount.class)
            .dependIf(CheckVip.class, ctx -> ctx.getResult(CheckVip.class))
        .node(NormalDiscount.class)
            .dependIfNot(CheckVip.class, ctx -> ctx.getResult(CheckVip.class))
        .node(BuildResult.class).depend(VipDiscount.class, NormalDiscount.class)
        .run(context);
```

```
                CheckVip
               /        \
   [if true]  /          \  [if false]
    VipDiscount    NormalDiscount
               \        /
              BuildResult       ← gets non-null result from the executed branch
```

- `dependIf(dep, predicate)` — add dependency and execute only if predicate returns `true`
- `dependIfNot(dep, predicate)` — add dependency and execute only if predicate returns `false`
- Skipped nodes complete with `null` result; downstream nodes still trigger
- The condition predicate is evaluated after all dependencies complete, so `ctx.getResult()` is safe to call

### Fallback / Default Values

Commands can define a `fallback()` method to return a degraded result when execution fails:

```java
public class FetchOrder implements AsyncCommand<OrderContext, Order> {
    @Override
    public Order run(OrderContext context) throws Exception {
        return orderService.getById(context.getOrderId());   // may throw
    }

    @Override
    public Order fallback(OrderContext context, Throwable cause) {
        return Order.empty();   // return a default/degraded result
    }

    @Override
    public boolean hasFallback() {
        return true;   // must return true to enable fallback
    }
}
```

- When a node fails (after all retries if retry is configured), `fallback(context, cause)` is invoked
- If fallback succeeds, the node completes normally with the fallback result
- Downstream nodes see the fallback result as a normal result
- If fallback itself throws, the exception propagates as usual
- `hasFallback()` must return `true` to activate the fallback mechanism

**Combining retry + fallback:**

```java
new JobBuilder<OrderContext>()
        .node(FetchOrder.class).retry(2, Duration.ofMillis(100))   // retry 2 times, then fallback
        .run(context);
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
│       │   ├── BatchStrategy.java           # Batch strategies: ALL, ANY, atLeast(N)
│       │   ├── context/                     # Context & injection interfaces
│       │   ├── depend/                      # Dependency declaration interface
│       │   └── function/                    # Lambda wrappers
│       ├── exception/                       # DagFlowBuildException, CycleException, etc.
│       ├── executor/                        # Default thread pool configuration
│       ├── model/                           # DagNode, DagNodeCheck, DagNodeFactory
│       ├── replay/                          # Replay profiling (DagFlowReplay, Printer)
│       ├── tracing/                         # OpenTelemetry tracing (DagFlowTracing)
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
    └── src/main/java/com/lesofn/dagflow/spring/boot/
        ├── autoconfigure/
        │   ├── DagFlowAutoConfiguration.java    # Auto-registers SpringContextHolder + replay beans
        │   └── DagFlowProperties.java           # dagflow.enabled, replay-enabled, etc.
        └── replay/
            ├── DagFlowReplayStore.java          # LRU cache for execution records
            └── DagFlowReplayController.java     # Web endpoint (/dagflow/replay)
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
