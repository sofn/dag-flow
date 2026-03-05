<div align="center">

# dag-flow

**A DAG-based parallel computation framework for Java**

Simplify multi-threaded task orchestration — declare dependencies, and the framework maximizes parallelism automatically.

[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
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
- **Hystrix integration** — `addHystrixNode()` wraps Netflix `HystrixCommand` into the DAG
- **Spring integration** — Optional; resolve Spring beans as DAG nodes via `dependSpringBean()`
- **Smart thread pools** — I/O pool (2x–8x cores) and CPU pool (cores+1) with `CallerRunsPolicy`
- **Error propagation** — Node exceptions propagate as `ExecutionException`; downstream nodes are cancelled

## Quick Start

### Installation

Add to your `build.gradle`:

```groovy
dependencies {
    implementation 'com.lesofn:dag-flow:1.0-SNAPSHOT'
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
│   ├── BatchCommand<C, P, R>          // Fan-out per param → Map<P, R>
│   └── HystrixCommandWrapper          // Netflix Hystrix adapter
└── CalcCommand<C, R>                  // Runs on CPU thread pool
        ├── FunctionCommand            // Lambda Function<C, R> wrapper
        └── ConsumerCommand            // Lambda Consumer<C> wrapper
```

### Core Components

| Component | Description |
|---|---|
| `JobBuilder<C>` | Fluent API for DAG construction and node registration |
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

Wrap existing `HystrixCommand` implementations:

```java
JobRunner<MyContext> runner = new JobBuilder<MyContext>()
        .addHystrixNode(MyHystrixCommand.class)
        .run(context);

String result = runner.getHystrixResult(MyHystrixCommand.class);
```

### Spring Integration

Resolve Spring beans as DAG nodes (requires `spring-context` on classpath):

```java
new JobBuilder<MyContext>()
        .addNode(MyService.class)
        .dependSpringBean("anotherService")
        .run(context);
```

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
src/main/java/com/lesofn/dagflow/
├── JobBuilder.java              # Fluent DAG construction
├── JobRunner.java               # CompletableFuture execution engine
├── api/
│   ├── DagFlowCommand.java      # Base command interface
│   ├── SyncCommand.java         # Synchronous command
│   ├── AsyncCommand.java        # Async (I/O) command
│   ├── CalcCommand.java         # CPU-bound command
│   ├── BatchCommand.java        # Batch fan-out command
│   ├── context/                 # Context & injection interfaces
│   ├── depend/                  # Dependency declaration interface
│   ├── function/                # Lambda wrappers
│   └── hystrix/                 # Hystrix adapter
├── exception/                   # DagFlowBuildException, CycleException, etc.
├── executor/                    # Default thread pool configuration
├── model/                       # DagNode, DagNodeCheck, DagNodeFactory
└── spring/                      # Optional Spring integration
```

## Build & Test

```bash
./gradlew build          # Build the project
./gradlew test           # Run all tests (Spock + JUnit Platform)
./gradlew clean build    # Clean build
```

## Requirements

- **Java** 17+
- **Gradle** 8.10+ (wrapper included)

## License

[Apache License 2.0](LICENSE)
