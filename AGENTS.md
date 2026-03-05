# dag-flow

DAG-based multi-threaded parallel computation framework in Java. Simplifies development of parallel tasks by automatically maximizing parallelism based on declared dependency relationships.

## Project Info

- **Language:** Java 17
- **Build:** Gradle 8.10.2 (wrapper)
- **Group:** `com.lesofn`
- **Package:** `com.lesofn.dagflow`

## Build & Test Commands

```bash
./gradlew build        # Build the project
./gradlew test         # Run tests (JUnit 5)
./gradlew clean build  # Clean build
```

## Architecture

The framework follows a DAG orchestration pattern:

1. **API Layer** (`api/`) — Command interfaces: `DagFlowCommand` (root), `SyncCommand`, `AsyncCommand`, `CalcCommand`, `BatchCommand`, plus lambda wrappers (`FunctionCommand`, `ConsumerCommand`) and Hystrix integration (`HystrixCommandWrapper`)
2. **Context** (`api/context/`) — `DagFlowContext` (abstract, user-subclassed) carries request data and provides access to dependency results
3. **Model** (`model/`) — `DagNode` (runtime node with future), `DagNodeFactory` (creation/registry), `DagNodeCheck` (DFS cycle detection)
4. **Builder/Runner** — `JobBuilder` (fluent DAG construction), `JobRunner` (CompletableFuture-based execution engine)
5. **Executor** (`executor/`) — Default thread pools: `ASYNC_DEFAULT_EXECUTOR` (I/O, 2x-8x CPU), `CALC_DEFAULT_EXECUTOR` (CPU, cores+1)
6. **Spring** (`spring/`) — Optional Spring integration via `SpringContextHolder`

## Key Dependencies

- `commons-lang3`, `commons-collections4`, `guava`, `jool`
- `hystrix-core` (Netflix Hystrix)
- `spring-context` (compileOnly — optional)
- `lombok` (compileOnly)
- JUnit 5 + Log4j2 (test)

## Test Structure

Tests are in `src/test/java/com/lesofn/dagflow/`:
- `test1/` — Basic DAG with 4 jobs
- `test2/` — Batch command tests
- `testerror/` — Error handling tests
- `hystrix/` — Hystrix integration tests
- `DagDagNodeCheckTest.java` — Cycle detection unit test

## Known Issues

- **Lombok / Java 21 incompatibility:** The project pins Lombok 1.18.22, which fails to compile on Java 21+. Upgrade to `1.18.34` in `build.gradle` to fix.
- **Flaky Hystrix test:** `hystrix/TestStarter.testHystrix01()` fails intermittently with `HystrixRuntimeException` / `TimeoutException` (timing-dependent).
