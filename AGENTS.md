# dag-flow

DAG-based multi-threaded parallel computation framework in Java. Simplifies development of parallel tasks by automatically maximizing parallelism based on declared dependency relationships.

## Project Info

- **Language:** Java 17
- **Build:** Gradle 8.10.2 (wrapper), multi-module project
- **Group:** `com.lesofn`
- **Package:** `com.lesofn.dagflow`

## Modules

| Module | Description | Key Dependencies |
|---|---|---|
| `dag-flow-core` | Core DAG framework: API, builder, runner, executor | commons-lang3, commons-collections4, guava, jool, slf4j |
| `dag-flow-hystrix` | Netflix Hystrix extension | dag-flow-core, hystrix-core |
| `dag-flow-resilience4j` | Resilience4j extension (circuit breaker, retry, bulkhead, rate limiter, time limiter) | dag-flow-core, resilience4j-* |

## Build & Test Commands

```bash
./gradlew build                    # Build all modules
./gradlew test                     # Run all tests (Spock/JUnit 5)
./gradlew :dag-flow-core:test     # Run core tests only
./gradlew :dag-flow-hystrix:test  # Run hystrix tests only
./gradlew :dag-flow-resilience4j:test  # Run resilience4j tests only
./gradlew clean build              # Clean build
```

## Architecture

### Core Module (`dag-flow-core`)

1. **API Layer** (`api/`) — Command interfaces: `DagFlowCommand` (root), `SyncCommand`, `AsyncCommand`, `CalcCommand`, `BatchCommand`, plus lambda wrappers (`FunctionCommand`, `ConsumerCommand`)
2. **Context** (`api/context/`) — `DagFlowContext` (abstract, user-subclassed), `DagFlowContextInjection` (context injection for extensions)
3. **Model** (`model/`) — `DagNode` (runtime node with future), `DagNodeFactory` (creation/registry), `DagNodeCheck` (DFS cycle detection)
4. **Builder/Runner** — `JobBuilder` (fluent DAG construction, extensible), `JobRunner` (CompletableFuture-based execution engine)
5. **Executor** (`executor/`) — Default thread pools: `ASYNC_DEFAULT_EXECUTOR` (I/O, 2x-8x CPU), `CALC_DEFAULT_EXECUTOR` (CPU, cores+1)
6. **Spring** (`spring/`) — Optional Spring integration via `SpringContextHolder`

### Hystrix Module (`dag-flow-hystrix`)

- `HystrixCommandWrapper` — Wraps `HystrixCommand` as `SyncCommand` (Hystrix manages its own threads)
- `HystrixJobBuilder` — Extends `JobBuilder` with `addHystrixNode()` methods

### Resilience4j Module (`dag-flow-resilience4j`)

- `Resilience4jCommand` — Wraps functions with Resilience4j decorators (CircuitBreaker, Retry, Bulkhead, RateLimiter, TimeLimiter)
- `Resilience4jJobBuilder` — Extends `JobBuilder` with `addResilience4jNode()` method

## Key Dependencies

- **Core:** `commons-lang3`, `commons-collections4`, `guava`, `jool`, `slf4j-api`
- **Core (optional):** `spring-context` (compileOnly)
- **Hystrix:** `hystrix-core` (Netflix Hystrix)
- **Resilience4j:** `resilience4j-circuitbreaker`, `resilience4j-timelimiter`, `resilience4j-ratelimiter`, `resilience4j-bulkhead`, `resilience4j-retry`
- **All:** `lombok` (compileOnly), Spock + Groovy + Log4j2 (test)

## Test Structure

### Core Tests (`dag-flow-core/src/test/`)
- `BasicDagSpec` — Basic DAG chain, dependNode, lambda, builder reuse (5 tests)
- `BatchDagSpec` — Batch command tests (2 tests)
- `DagNodeCheckSpec` — Cycle detection unit tests (9 tests)
- `DagScenarioSpec` — Comprehensive scenario tests (33 tests)
- `ErrorHandlingSpec` — Error propagation tests (2 tests)
- Test fixtures: `test1/`, `test2/`, `testerror/`

### Hystrix Tests (`dag-flow-hystrix/src/test/`)
- `HystrixDagSpec` — Hystrix integration tests (5 tests)
- Test fixtures: `HystrixContext`, `HystrixWrapperJob`, `OriginHystrixJob`

### Resilience4j Tests (`dag-flow-resilience4j/src/test/`)
- `Resilience4jDagSpec` — Resilience4j integration tests (10 tests)
