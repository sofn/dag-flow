package com.lesofn.dagflow.spring.boot.replay;

import com.lesofn.dagflow.replay.DagFlowReplay;
import com.lesofn.dagflow.replay.NodeReplayRecord;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller that exposes DAG replay records via HTTP.
 * <ul>
 *   <li>GET /dagflow/replay — HTML list of recent executions</li>
 *   <li>GET /dagflow/replay/{id} — HTML detail with waterfall visualization</li>
 *   <li>GET /dagflow/replay/{id}/text — plain text Gantt + timeline</li>
 * </ul>
 *
 * @author sofn
 */
@RestController
@RequestMapping("/dagflow/replay")
public class DagFlowReplayController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final DagFlowReplayStore store;

    public DagFlowReplayController(DagFlowReplayStore store) {
        this.store = store;
    }

    /**
     * List all cached replay records as HTML.
     */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String list() {
        List<DagFlowReplay> records = store.getAll();
        StringBuilder html = new StringBuilder();
        html.append(HTML_HEAD);
        html.append("<body><div class='container'>");
        html.append("<h1>DAG Flow Replay Records</h1>");
        html.append("<p>Cached records: ").append(records.size()).append("</p>");
        html.append("<table><tr><th>ID</th><th>Time</th><th>Nodes</th><th>Duration</th><th>Status</th><th>Action</th></tr>");
        // Reverse order: most recent first
        for (int i = records.size() - 1; i >= 0; i--) {
            DagFlowReplay r = records.get(i);
            String statusClass = r.isSuccess() ? "success" : "error";
            html.append("<tr>")
                    .append("<td><code>").append(r.getId()).append("</code></td>")
                    .append("<td>").append(TIME_FMT.format(r.getStartTime())).append("</td>")
                    .append("<td>").append(r.getNodeCount()).append("</td>")
                    .append("<td>").append(r.getTotalDurationMs()).append("ms</td>")
                    .append("<td class='").append(statusClass).append("'>").append(r.isSuccess() ? "SUCCESS" : "FAILED").append("</td>")
                    .append("<td><a href='/dagflow/replay/").append(r.getId()).append("'>View</a></td>")
                    .append("</tr>");
        }
        html.append("</table></div></body></html>");
        return html.toString();
    }

    /**
     * Detail view of a single replay record with waterfall chart.
     */
    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> detail(@PathVariable String id) {
        DagFlowReplay replay = store.get(id);
        if (replay == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(renderDetailHtml(replay));
    }

    /**
     * Plain text rendering (Gantt + Timeline).
     */
    @GetMapping(value = "/{id}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> text(@PathVariable String id) {
        DagFlowReplay replay = store.get(id);
        if (replay == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(replay.toGantt() + "\n" + replay.toTimeline());
    }

    /**
     * JSON API: list all records.
     */
    @GetMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ReplaySummary> apiList() {
        return store.getAll().stream().map(r -> new ReplaySummary(
                r.getId(),
                TIME_FMT.format(r.getStartTime()),
                r.getNodeCount(),
                r.getTotalDurationMs(),
                r.isSuccess()
        )).collect(Collectors.toList());
    }

    /**
     * JSON API: single record detail.
     */
    @GetMapping(value = "/api/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DagFlowReplay> apiDetail(@PathVariable String id) {
        DagFlowReplay replay = store.get(id);
        if (replay == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(replay);
    }

    // ======================== HTML Rendering ========================

    private String renderDetailHtml(DagFlowReplay replay) {
        long totalMs = Math.max(replay.getTotalDurationMs(), 1);
        StringBuilder html = new StringBuilder();
        html.append(HTML_HEAD);
        html.append("<body><div class='container'>");
        html.append("<p><a href='/dagflow/replay'>&larr; Back to list</a></p>");
        html.append("<h1>DAG Execution ").append(replay.getId()).append("</h1>");
        html.append("<div class='info'>");
        html.append("<span>Time: ").append(TIME_FMT.format(replay.getStartTime())).append("</span>");
        html.append("<span>Nodes: ").append(replay.getNodeCount()).append("</span>");
        html.append("<span>Duration: ").append(totalMs).append("ms</span>");
        String statusClass = replay.isSuccess() ? "success" : "error";
        html.append("<span class='").append(statusClass).append("'>").append(replay.isSuccess() ? "SUCCESS" : "FAILED").append("</span>");
        html.append("</div>");

        // Waterfall chart
        html.append("<h2>Waterfall Timeline</h2>");
        html.append("<div class='waterfall'>");

        // Time ruler
        html.append("<div class='ruler'>");
        html.append("<div class='label'>&nbsp;</div>");
        html.append("<div class='bar-area'>");
        for (int i = 0; i <= 5; i++) {
            long ms = totalMs * i / 5;
            double pct = 100.0 * i / 5;
            html.append("<span class='tick' style='left:").append(String.format("%.1f", pct)).append("%'>").append(ms).append("ms</span>");
        }
        html.append("</div></div>");

        // Node rows
        for (NodeReplayRecord node : replay.getNodeRecords()) {
            double startPct = 100.0 * node.getStartOffsetMs() / totalMs;
            double widthPct = Math.max(100.0 * node.getDurationMs() / totalMs, 0.5);
            String barClass = node.isSuccess() ? "bar" : "bar bar-error";
            String deps = node.getDependencies().isEmpty() ? "" : " \u2190 " + String.join(", ", node.getDependencies());

            html.append("<div class='row'>");
            html.append("<div class='label' title='").append(node.getNodeType()).append(deps).append("'>");
            html.append(node.getNodeName());
            html.append("</div>");
            html.append("<div class='bar-area'>");
            html.append("<div class='").append(barClass).append("' style='left:")
                    .append(String.format("%.2f", startPct)).append("%;width:")
                    .append(String.format("%.2f", widthPct)).append("%' title='")
                    .append(node.getStartOffsetMs()).append("-").append(node.getEndOffsetMs())
                    .append("ms (").append(node.getDurationMs()).append("ms) ")
                    .append(node.getNodeType()).append(" @ ").append(node.getThreadName())
                    .append("'>");
            html.append(node.getDurationMs()).append("ms");
            html.append("</div></div></div>");
        }
        html.append("</div>");

        // Details table
        html.append("<h2>Node Details</h2>");
        html.append("<table><tr><th>Node</th><th>Type</th><th>Start</th><th>End</th><th>Duration</th><th>Thread</th><th>Deps</th><th>Status</th></tr>");
        for (NodeReplayRecord node : replay.getNodeRecords()) {
            String nodeStatusClass = node.isSuccess() ? "success" : "error";
            html.append("<tr>")
                    .append("<td><code>").append(node.getNodeName()).append("</code></td>")
                    .append("<td>").append(node.getNodeType()).append("</td>")
                    .append("<td>").append(node.getStartOffsetMs()).append("ms</td>")
                    .append("<td>").append(node.getEndOffsetMs()).append("ms</td>")
                    .append("<td><b>").append(node.getDurationMs()).append("ms</b></td>")
                    .append("<td><code>").append(node.getThreadName()).append("</code></td>")
                    .append("<td>").append(node.getDependencies().isEmpty() ? "-" : String.join(", ", node.getDependencies())).append("</td>")
                    .append("<td class='").append(nodeStatusClass).append("'>").append(node.isSuccess() ? "OK" : node.getErrorMessage()).append("</td>")
                    .append("</tr>");
        }
        html.append("</table>");

        // Text view
        html.append("<h2>Text View</h2>");
        html.append("<pre>").append(escapeHtml(replay.toGantt())).append("\n").append(escapeHtml(replay.toTimeline())).append("</pre>");

        html.append("</div></body></html>");
        return html.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ======================== CSS / HTML Head ========================

    private static final String HTML_HEAD = """
            <!DOCTYPE html>
            <html lang="en"><head>
            <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>DAG Flow Replay</title>
            <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #333; }
            .container { max-width: 1200px; margin: 20px auto; padding: 0 20px; }
            h1 { margin: 20px 0 10px; font-size: 1.5em; }
            h2 { margin: 20px 0 10px; font-size: 1.2em; color: #555; }
            p, .info { margin: 8px 0; }
            .info span { margin-right: 20px; display: inline-block; }
            a { color: #0066cc; text-decoration: none; }
            a:hover { text-decoration: underline; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0; background: white; border-radius: 4px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
            th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; font-size: 0.9em; }
            th { background: #f8f8f8; font-weight: 600; }
            .success { color: #28a745; font-weight: 600; }
            .error { color: #dc3545; font-weight: 600; }
            pre { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 4px; overflow-x: auto; font-size: 0.85em; line-height: 1.5; }
            code { background: #e9ecef; padding: 2px 6px; border-radius: 3px; font-size: 0.85em; }
            .waterfall { background: white; border-radius: 4px; padding: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
            .waterfall .row, .waterfall .ruler { display: flex; align-items: center; min-height: 30px; border-bottom: 1px solid #f0f0f0; }
            .waterfall .label { width: 180px; min-width: 180px; padding: 4px 8px; font-size: 0.85em; font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .waterfall .bar-area { flex: 1; position: relative; height: 24px; background: #fafafa; }
            .waterfall .bar { position: absolute; height: 20px; top: 2px; background: linear-gradient(135deg, #4CAF50, #66BB6A); border-radius: 3px; color: white; font-size: 0.7em; line-height: 20px; padding: 0 4px; overflow: hidden; white-space: nowrap; min-width: 2px; }
            .waterfall .bar-error { background: linear-gradient(135deg, #f44336, #e57373); }
            .waterfall .tick { position: absolute; top: 0; font-size: 0.7em; color: #999; transform: translateX(-50%); }
            .waterfall .ruler { border-bottom: 2px solid #ddd; }
            .waterfall .ruler .bar-area { height: 20px; }
            </style></head>
            """;

    // ======================== DTO ========================

    public record ReplaySummary(String id, String time, int nodeCount, long durationMs, boolean success) {
    }
}
