package com.lesofn.dagflow.replay;

import java.util.List;

/**
 * Immutable record of a single node execution.
 *
 * @author sofn
 */
public class NodeReplayRecord {

    private final String nodeName;
    private final String nodeType;
    private final String threadName;
    private final List<String> dependencies;
    private final long startTimeMs;
    private final long endTimeMs;
    private final long startOffsetMs;
    private final long endOffsetMs;
    private final boolean success;
    private final String errorMessage;

    public NodeReplayRecord(String nodeName, String nodeType, String threadName,
                            List<String> dependencies,
                            long startTimeMs, long endTimeMs,
                            long startOffsetMs, long endOffsetMs,
                            boolean success, String errorMessage) {
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.threadName = threadName;
        this.dependencies = dependencies;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.startOffsetMs = startOffsetMs;
        this.endOffsetMs = endOffsetMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getThreadName() {
        return threadName;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    /**
     * Start time offset from DAG start (milliseconds)
     */
    public long getStartOffsetMs() {
        return startOffsetMs;
    }

    /**
     * End time offset from DAG start (milliseconds)
     */
    public long getEndOffsetMs() {
        return endOffsetMs;
    }

    /**
     * Duration in milliseconds
     */
    public long getDurationMs() {
        return endOffsetMs - startOffsetMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
