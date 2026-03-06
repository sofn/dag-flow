package com.lesofn.dagflow.benchmark;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.api.CalcCommand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

/**
 * dag-flow 性能对比测试：传统线程池 vs 虚拟线程
 * <p>
 * 通过 {@code ./gradlew :dag-flow-core:benchmark} 单独运行，不包含在默认 test 中。
 *
 * @author sofn
 */
@Tag("benchmark")
public class DagFlowBenchmark {

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURE_ROUNDS = 5;

    // ======================== I/O 密集型场景 ========================

    /**
     * 场景1: 10个并行 I/O 节点，每个模拟 100ms 网络调用
     */
    @Test
    void benchmark_io_10_parallel_nodes() throws Exception {
        System.out.println("\n========== Scenario 1: 10 Parallel I/O Nodes (100ms each) ==========");
        int nodeCount = 10;
        int sleepMs = 100;

        double platformAvg = runBenchmark("Platform Thread Pool", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<>();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.funcNode("io_" + idx, (Function<BenchmarkContext, Object>) ctx -> {
                    simulateIO(sleepMs);
                    return "io_" + idx;
                });
            }
            builder.run(new BenchmarkContext());
        });

        double virtualAvg = runBenchmark("Virtual Threads", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<BenchmarkContext>().useVirtualThreads();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.funcNode("io_" + idx, (Function<BenchmarkContext, Object>) ctx -> {
                    simulateIO(sleepMs);
                    return "io_" + idx;
                });
            }
            builder.run(new BenchmarkContext());
        });

        printComparison(platformAvg, virtualAvg);
    }

    /**
     * 场景2: 50个并行 I/O 节点，每个模拟 50ms 网络调用
     */
    @Test
    void benchmark_io_50_parallel_nodes() throws Exception {
        System.out.println("\n========== Scenario 2: 50 Parallel I/O Nodes (50ms each) ==========");
        int nodeCount = 50;
        int sleepMs = 50;

        double platformAvg = runBenchmark("Platform Thread Pool", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<>();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.funcNode("io_" + idx, (Function<BenchmarkContext, Object>) ctx -> {
                    simulateIO(sleepMs);
                    return "io_" + idx;
                });
            }
            builder.run(new BenchmarkContext());
        });

        double virtualAvg = runBenchmark("Virtual Threads", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<BenchmarkContext>().useVirtualThreads();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.funcNode("io_" + idx, (Function<BenchmarkContext, Object>) ctx -> {
                    simulateIO(sleepMs);
                    return "io_" + idx;
                });
            }
            builder.run(new BenchmarkContext());
        });

        printComparison(platformAvg, virtualAvg);
    }

    /**
     * 场景3: 100个并行 I/O 节点，每个模拟 20ms 网络调用
     */
    @Test
    void benchmark_io_100_parallel_nodes() throws Exception {
        System.out.println("\n========== Scenario 3: 100 Parallel I/O Nodes (20ms each) ==========");
        int nodeCount = 100;
        int sleepMs = 20;

        double platformAvg = runBenchmark("Platform Thread Pool", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<>();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.funcNode("io_" + idx, (Function<BenchmarkContext, Object>) ctx -> {
                    simulateIO(sleepMs);
                    return "io_" + idx;
                });
            }
            builder.run(new BenchmarkContext());
        });

        double virtualAvg = runBenchmark("Virtual Threads", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<BenchmarkContext>().useVirtualThreads();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.funcNode("io_" + idx, (Function<BenchmarkContext, Object>) ctx -> {
                    simulateIO(sleepMs);
                    return "io_" + idx;
                });
            }
            builder.run(new BenchmarkContext());
        });

        printComparison(platformAvg, virtualAvg);
    }

    // ======================== CPU 密集型场景 ========================

    /**
     * 场景4: 8个并行 CPU 密集型节点
     */
    @Test
    void benchmark_cpu_8_parallel_nodes() throws Exception {
        System.out.println("\n========== Scenario 4: 8 Parallel CPU Nodes ==========");
        int nodeCount = 8;

        double platformAvg = runBenchmark("Platform Thread Pool", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<>();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.addNode("cpu_" + idx, (CalcCommand<BenchmarkContext, Long>) ctx -> simulateCPU());
            }
            builder.run(new BenchmarkContext());
        });

        double virtualAvg = runBenchmark("Virtual Threads", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            JobBuilder<BenchmarkContext> builder = new JobBuilder<BenchmarkContext>().useVirtualThreads();
            for (int i = 0; i < nodeCount; i++) {
                int idx = i;
                builder.addNode("cpu_" + idx, (CalcCommand<BenchmarkContext, Long>) ctx -> simulateCPU());
            }
            builder.run(new BenchmarkContext());
        });

        printComparison(platformAvg, virtualAvg);
    }

    // ======================== 混合场景 ========================

    /**
     * 场景5: DAG 混合拓扑 — I/O 扇出 → CPU 汇聚 → I/O 最终节点
     * <pre>
     *   io_0  io_1  io_2  io_3  io_4   (5 parallel I/O, 100ms each)
     *     \    |    |    |    /
     *          aggregate              (CPU: sum results)
     *              |
     *          final_io               (I/O: 50ms)
     * </pre>
     */
    @Test
    void benchmark_mixed_dag_topology() throws Exception {
        System.out.println("\n========== Scenario 5: Mixed DAG (5 I/O → CPU aggregate → I/O final) ==========");

        double platformAvg = runBenchmark("Platform Thread Pool", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            buildMixedDag(new JobBuilder<>()).run(new BenchmarkContext());
        });

        double virtualAvg = runBenchmark("Virtual Threads", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            buildMixedDag(new JobBuilder<BenchmarkContext>().useVirtualThreads()).run(new BenchmarkContext());
        });

        printComparison(platformAvg, virtualAvg);
    }

    /**
     * 场景6: 多层级 DAG — 3 层各 10 个节点，层间有依赖
     * <pre>
     *   Layer 0: io_0_0 ~ io_0_9  (10 parallel, 30ms each)
     *   Layer 1: io_1_0 ~ io_1_9  (each depends on corresponding Layer 0 node, 30ms each)
     *   Layer 2: io_2_0 ~ io_2_9  (each depends on corresponding Layer 1 node, 30ms each)
     * </pre>
     */
    @Test
    void benchmark_multi_layer_dag() throws Exception {
        System.out.println("\n========== Scenario 6: Multi-Layer DAG (3 layers × 10 nodes, 30ms each) ==========");
        int layers = 3;
        int nodesPerLayer = 10;
        int sleepMs = 30;

        double platformAvg = runBenchmark("Platform Thread Pool", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            buildMultiLayerDag(new JobBuilder<>(), layers, nodesPerLayer, sleepMs)
                    .run(new BenchmarkContext());
        });

        double virtualAvg = runBenchmark("Virtual Threads", WARMUP_ROUNDS, MEASURE_ROUNDS, () -> {
            buildMultiLayerDag(new JobBuilder<BenchmarkContext>().useVirtualThreads(), layers, nodesPerLayer, sleepMs)
                    .run(new BenchmarkContext());
        });

        printComparison(platformAvg, virtualAvg);
    }

    // ======================== 辅助方法 ========================

    private JobBuilder<BenchmarkContext> buildMixedDag(JobBuilder<BenchmarkContext> builder) {
        for (int i = 0; i < 5; i++) {
            int idx = i;
            builder.funcNode("io_" + idx, (Function<BenchmarkContext, Object>) ctx -> {
                simulateIO(100);
                return idx;
            });
        }
        builder.addNode("aggregate", (CalcCommand<BenchmarkContext, Long>) ctx -> simulateCPU());
        for (int i = 0; i < 5; i++) {
            builder.depend("io_" + i);
        }
        builder.funcNode("final_io", (Function<BenchmarkContext, Object>) ctx -> {
            simulateIO(50);
            return "done";
        }).depend("aggregate");
        return builder;
    }

    private JobBuilder<BenchmarkContext> buildMultiLayerDag(
            JobBuilder<BenchmarkContext> builder, int layers, int nodesPerLayer, int sleepMs) {
        for (int layer = 0; layer < layers; layer++) {
            for (int n = 0; n < nodesPerLayer; n++) {
                String nodeName = "io_" + layer + "_" + n;
                builder.funcNode(nodeName, (Function<BenchmarkContext, Object>) ctx -> {
                    simulateIO(sleepMs);
                    return nodeName;
                });
                if (layer > 0) {
                    builder.depend("io_" + (layer - 1) + "_" + n);
                }
            }
        }
        return builder;
    }

    private static void simulateIO(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long simulateCPU() {
        long sum = 0;
        for (int i = 0; i < 5_000_000; i++) {
            sum += i * i;
        }
        return sum;
    }

    @FunctionalInterface
    interface BenchmarkTask {
        void run() throws Exception;
    }

    private double runBenchmark(String label, int warmup, int measure, BenchmarkTask task) throws Exception {
        // Warmup
        for (int i = 0; i < warmup; i++) {
            task.run();
        }

        // Measure
        long[] times = new long[measure];
        for (int i = 0; i < measure; i++) {
            long start = System.nanoTime();
            task.run();
            times[i] = System.nanoTime() - start;
        }

        double avgMs = 0;
        for (long t : times) {
            avgMs += t / 1_000_000.0;
        }
        avgMs /= measure;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %-25s avg: %8.2f ms  [", label, avgMs));
        for (int i = 0; i < times.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.2f", times[i] / 1_000_000.0));
        }
        sb.append(" ms]");
        System.out.println(sb);

        return avgMs;
    }

    private void printComparison(double platformMs, double virtualMs) {
        double ratio = platformMs / virtualMs;
        String winner = virtualMs < platformMs ? "Virtual Threads" : "Platform Threads";
        double pct = Math.abs(platformMs - virtualMs) / Math.max(platformMs, virtualMs) * 100;
        System.out.printf("  → %s is %.1f%% faster (%.2fx)%n", winner, pct, ratio);
    }
}
