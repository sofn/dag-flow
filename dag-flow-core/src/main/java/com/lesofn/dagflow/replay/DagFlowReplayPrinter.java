package com.lesofn.dagflow.replay;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders {@link DagFlowReplay} as text-based visualizations:
 * <ul>
 *   <li>{@link #gantt} — Gantt chart showing node bars proportional to duration</li>
 *   <li>{@link #timeline} — Chrome DevTools waterfall-style timeline</li>
 * </ul>
 *
 * @author sofn
 */
public final class DagFlowReplayPrinter {

    private static final int BAR_WIDTH = 50;
    private static final char BLOCK_FULL = '\u2588';   // █
    private static final char BLOCK_WAIT = '\u2591';   // ░
    private static final char BLOCK_ERR = '\u2593';    // ▓

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private DagFlowReplayPrinter() {
    }

    // ======================== Gantt Chart ========================

    /**
     * Render a text-based Gantt chart.
     * <pre>
     * DAG Execution [8 nodes, 250ms, SUCCESS]
     * ──────────────────────────────────────────────────────────────
     * FetchOrder   |████████████                                  |   0- 120ms [Async]
     * FetchUser    |██████                                        |   0-  60ms [Async]
     * CalcDiscount |            ██████████████                    | 120- 220ms [Calc]
     * BuildResult  |                          ████                | 220- 250ms [Sync]
     * ──────────────────────────────────────────────────────────────
     * </pre>
     */
    public static String gantt(DagFlowReplay replay) {
        if (replay == null || replay.getNodeRecords().isEmpty()) {
            return "No replay data.";
        }

        List<NodeReplayRecord> nodes = replay.getNodeRecords();
        long totalMs = Math.max(replay.getTotalDurationMs(), 1);
        int nameWidth = maxNameWidth(nodes);

        StringBuilder sb = new StringBuilder();
        String status = replay.isSuccess() ? "SUCCESS" : "FAILED";
        sb.append(String.format("DAG Execution [%d nodes, %dms, %s]  %s%n",
                replay.getNodeCount(), totalMs, status,
                TIME_FMT.format(replay.getStartTime())));
        appendDivider(sb, nameWidth);

        for (NodeReplayRecord node : nodes) {
            long startOff = node.getStartOffsetMs();
            long endOff = node.getEndOffsetMs();
            int barStart = (int) (startOff * BAR_WIDTH / totalMs);
            int barEnd = (int) (endOff * BAR_WIDTH / totalMs);
            barEnd = Math.max(barEnd, barStart + 1);
            barStart = Math.min(barStart, BAR_WIDTH - 1);
            barEnd = Math.min(barEnd, BAR_WIDTH);

            char fillChar = node.isSuccess() ? BLOCK_FULL : BLOCK_ERR;
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < BAR_WIDTH; i++) {
                if (i >= barStart && i < barEnd) {
                    bar.append(fillChar);
                } else {
                    bar.append(' ');
                }
            }

            sb.append(String.format("%-" + nameWidth + "s |%s| %3d-%4dms [%s]%n",
                    node.getNodeName(), bar, startOff, endOff, shortType(node.getNodeType())));
        }

        appendDivider(sb, nameWidth);
        return sb.toString();
    }

    // ======================== Timeline (Chrome DevTools style) ========================

    /**
     * Render a text-based waterfall timeline similar to Chrome DevTools Network tab.
     * <pre>
     * DAG Timeline [250ms]
     * ──────────────────────────────────────────────────────────────
     *              0ms   50ms  100ms 150ms 200ms 250ms
     *              |     |     |     |     |     |
     * FetchOrder   ░░░░░░████████████░░░░░░░░░░░░  120ms  Async  pool-1-thread-2
     * FetchUser    ░░░░░░██████░░░░░░░░░░░░░░░░░░   60ms  Async  pool-1-thread-3
     * CalcDiscount ░░░░░░░░░░░░████████████░░░░░░  100ms  Calc   ForkJoin-1
     * BuildResult  ░░░░░░░░░░░░░░░░░░░░████░░░░░░   30ms  Sync   main
     * ──────────────────────────────────────────────────────────────
     * </pre>
     */
    public static String timeline(DagFlowReplay replay) {
        if (replay == null || replay.getNodeRecords().isEmpty()) {
            return "No replay data.";
        }

        List<NodeReplayRecord> nodes = replay.getNodeRecords();
        long totalMs = Math.max(replay.getTotalDurationMs(), 1);
        int nameWidth = maxNameWidth(nodes);

        StringBuilder sb = new StringBuilder();
        String status = replay.isSuccess() ? "SUCCESS" : "FAILED";
        sb.append(String.format("DAG Timeline [%d nodes, %dms, %s]  %s%n",
                replay.getNodeCount(), totalMs, status,
                TIME_FMT.format(replay.getStartTime())));
        appendDivider(sb, nameWidth);

        // Time ruler
        appendRuler(sb, nameWidth, totalMs);

        for (NodeReplayRecord node : nodes) {
            long startOff = node.getStartOffsetMs();
            long endOff = node.getEndOffsetMs();
            int barStart = (int) (startOff * BAR_WIDTH / totalMs);
            int barEnd = (int) (endOff * BAR_WIDTH / totalMs);
            barEnd = Math.max(barEnd, barStart + 1);
            barStart = Math.min(barStart, BAR_WIDTH - 1);
            barEnd = Math.min(barEnd, BAR_WIDTH);

            char fillChar = node.isSuccess() ? BLOCK_FULL : BLOCK_ERR;
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < BAR_WIDTH; i++) {
                if (i >= barStart && i < barEnd) {
                    bar.append(fillChar);
                } else {
                    bar.append(BLOCK_WAIT);
                }
            }

            sb.append(String.format("%-" + nameWidth + "s %s %4dms  %-6s %s%n",
                    node.getNodeName(), bar, node.getDurationMs(),
                    shortType(node.getNodeType()), node.getThreadName()));
        }

        appendDivider(sb, nameWidth);
        return sb.toString();
    }

    // ======================== Helpers ========================

    private static int maxNameWidth(List<NodeReplayRecord> nodes) {
        int max = 0;
        for (NodeReplayRecord n : nodes) {
            max = Math.max(max, n.getNodeName().length());
        }
        return Math.max(max, 4);
    }

    private static void appendDivider(StringBuilder sb, int nameWidth) {
        sb.append("\u2500".repeat(nameWidth + BAR_WIDTH + 30)).append('\n');
    }

    private static void appendRuler(StringBuilder sb, int nameWidth, long totalMs) {
        // Calculate 5 evenly spaced time markers
        int markers = 6;
        long step = totalMs / (markers - 1);
        if (step <= 0) step = 1;

        sb.append(String.format("%-" + nameWidth + "s ", ""));
        for (int i = 0; i < markers; i++) {
            long ms = Math.min(i * step, totalMs);
            String label = ms + "ms";
            int pos = (int) (ms * BAR_WIDTH / totalMs);
            if (i == 0) {
                sb.append(label);
            } else {
                int gap = (int) (step * BAR_WIDTH / totalMs) - (i > 1 ? ((i - 1) * step + "ms").length() : ("" + 0 + "ms").length());
                if (gap < 0) gap = 1;
                // Just use fixed spacing
            }
        }
        // Simple fixed ruler
        sb.setLength(sb.length() - ((sb.length() > nameWidth + 2) ? sb.length() - nameWidth - 1 : 0));
        sb.append(String.format("%-" + nameWidth + "s ", ""));
        StringBuilder ruler = new StringBuilder();
        for (int i = 0; i < markers && i < 6; i++) {
            long ms = Math.min(i * step, totalMs);
            String label = String.format("%-6s", ms + "ms");
            int targetPos = (int) (ms * BAR_WIDTH / totalMs);
            while (ruler.length() < targetPos) ruler.append(' ');
            ruler.append(label);
        }
        sb.append(ruler).append('\n');

        sb.append(String.format("%-" + nameWidth + "s ", ""));
        StringBuilder ticks = new StringBuilder();
        for (int i = 0; i < markers && i < 6; i++) {
            long ms = Math.min(i * step, totalMs);
            int targetPos = (int) (ms * BAR_WIDTH / totalMs);
            while (ticks.length() < targetPos) ticks.append(' ');
            ticks.append('|');
        }
        sb.append(ticks).append('\n');
    }

    private static String shortType(String nodeType) {
        if (nodeType == null) return "?";
        return switch (nodeType) {
            case "AsyncCommand" -> "Async";
            case "SyncCommand" -> "Sync";
            case "CalcCommand" -> "Calc";
            case "BatchCommand" -> "Batch";
            default -> nodeType;
        };
    }
}
