package com.lesofn.dagflow.replay;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable record of a complete DAG execution, including timing data
 * for every node. Use {@link DagFlowReplayPrinter} to render as text.
 *
 * @author sofn
 */
public class DagFlowReplay {

    private final String id;
    private final Instant startTime;
    private final Instant endTime;
    private final long totalDurationMs;
    private final int nodeCount;
    private final boolean success;
    private final List<NodeReplayRecord> nodeRecords;

    public DagFlowReplay(String id, Instant startTime, Instant endTime,
                         long totalDurationMs, int nodeCount, boolean success,
                         List<NodeReplayRecord> nodeRecords) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalDurationMs = totalDurationMs;
        this.nodeCount = nodeCount;
        this.success = success;
        this.nodeRecords = Collections.unmodifiableList(nodeRecords);
    }

    public String getId() {
        return id;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<NodeReplayRecord> getNodeRecords() {
        return nodeRecords;
    }

    /**
     * Print text-based Gantt chart
     */
    public String toGantt() {
        return DagFlowReplayPrinter.gantt(this);
    }

    /**
     * Print text-based waterfall timeline (Chrome DevTools style)
     */
    public String toTimeline() {
        return DagFlowReplayPrinter.timeline(this);
    }

    @Override
    public String toString() {
        return "DagFlowReplay{id='" + id + "', nodes=" + nodeCount
                + ", duration=" + totalDurationMs + "ms, success=" + success + "}";
    }
}
