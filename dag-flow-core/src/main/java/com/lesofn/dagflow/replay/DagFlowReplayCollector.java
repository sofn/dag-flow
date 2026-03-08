package com.lesofn.dagflow.replay;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe collector that records timing data during DAG execution.
 * After execution completes, call {@link #build()} to produce an immutable
 * {@link DagFlowReplay} record.
 *
 * @author sofn
 */
public class DagFlowReplayCollector {

    private final String id;
    private final long dagStartNanos;
    private final long dagStartMs;
    private volatile long dagEndNanos;
    private volatile boolean dagSuccess = true;

    /**
     * Per-node mutable recording entries, keyed by node name.
     */
    private final Map<String, NodeEntry> entries = new ConcurrentHashMap<>();

    /**
     * Preserves insertion order for output rendering.
     */
    private final List<String> nodeOrder = Collections.synchronizedList(new ArrayList<>());

    public DagFlowReplayCollector() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.dagStartNanos = System.nanoTime();
        this.dagStartMs = System.currentTimeMillis();
    }

    /**
     * Register a node (call during Phase 1 wiring, preserves order).
     */
    public void registerNode(String nodeName, String nodeType, List<String> dependencies) {
        NodeEntry entry = new NodeEntry();
        entry.nodeType = nodeType;
        entry.dependencies = dependencies;
        entries.put(nodeName, entry);
        nodeOrder.add(nodeName);
    }

    /**
     * Record node execution start (called from the executing thread).
     */
    public void onNodeStart(String nodeName) {
        NodeEntry entry = entries.get(nodeName);
        if (entry != null) {
            entry.startNanos = System.nanoTime();
            entry.threadName = Thread.currentThread().getName();
        }
    }

    /**
     * Record node execution end (called from the executing thread).
     */
    public void onNodeEnd(String nodeName, boolean success, Throwable error) {
        NodeEntry entry = entries.get(nodeName);
        if (entry != null) {
            entry.endNanos = System.nanoTime();
            entry.success = success;
            entry.errorMessage = error != null ? error.getMessage() : null;
        }
    }

    /**
     * Mark the entire DAG execution as finished.
     */
    public void onDagEnd(boolean success) {
        this.dagEndNanos = System.nanoTime();
        this.dagSuccess = success;
    }

    /**
     * Build the immutable replay record. Call after DAG execution completes.
     */
    public DagFlowReplay build() {
        long totalDurationMs = nanosToMs(dagEndNanos - dagStartNanos);
        Instant startTime = Instant.ofEpochMilli(dagStartMs);
        Instant endTime = Instant.ofEpochMilli(dagStartMs + totalDurationMs);

        List<NodeReplayRecord> records = new ArrayList<>();
        for (String nodeName : nodeOrder) {
            NodeEntry entry = entries.get(nodeName);
            if (entry == null) {
                continue;
            }
            long startOffsetMs = entry.startNanos > 0 ? nanosToMs(entry.startNanos - dagStartNanos) : 0;
            long endOffsetMs = entry.endNanos > 0 ? nanosToMs(entry.endNanos - dagStartNanos) : totalDurationMs;
            long startMs = dagStartMs + startOffsetMs;
            long endMs = dagStartMs + endOffsetMs;

            records.add(new NodeReplayRecord(
                    nodeName,
                    entry.nodeType,
                    entry.threadName != null ? entry.threadName : "unknown",
                    entry.dependencies != null ? entry.dependencies : Collections.emptyList(),
                    startMs, endMs,
                    startOffsetMs, endOffsetMs,
                    entry.success,
                    entry.errorMessage
            ));
        }

        return new DagFlowReplay(id, startTime, endTime, totalDurationMs,
                records.size(), dagSuccess, records);
    }

    private static long nanosToMs(long nanos) {
        return Math.max(0, nanos / 1_000_000);
    }

    private static class NodeEntry {
        volatile String nodeType;
        volatile List<String> dependencies;
        volatile long startNanos;
        volatile long endNanos;
        volatile String threadName;
        volatile boolean success = true;
        volatile String errorMessage;
    }
}
