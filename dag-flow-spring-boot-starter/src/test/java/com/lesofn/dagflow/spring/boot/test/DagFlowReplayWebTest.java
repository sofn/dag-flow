package com.lesofn.dagflow.spring.boot.test;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.JobRunner;
import com.lesofn.dagflow.replay.DagFlowReplay;
import com.lesofn.dagflow.spring.boot.replay.DagFlowReplayController;
import com.lesofn.dagflow.spring.boot.replay.DagFlowReplayStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = TestApplication.class,
        properties = {"dagflow.replay-enabled=true", "dagflow.replay-cache-size=10"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class DagFlowReplayWebTest {

    @Autowired
    DagFlowReplayStore replayStore;

    @Autowired
    DagFlowReplayController replayController;

    @Test
    void replayStoreIsAutoConfigured() {
        assertNotNull(replayStore);
    }

    @Test
    void replayControllerIsAutoConfigured() {
        assertNotNull(replayController);
    }

    @Test
    void listEndpointReturnsHtml() {
        String html = replayController.list();
        assertNotNull(html);
        assertTrue(html.contains("DAG Flow Replay Records"));
    }

    @Test
    void detailEndpointReturns404ForUnknownId() {
        ResponseEntity<String> response = replayController.detail("nonexistent");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void textEndpointReturns404ForUnknownId() {
        ResponseEntity<String> response = replayController.text("nonexistent");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void fullReplayWorkflow() throws Exception {
        // Execute a DAG with replay enabled
        TestSpringContext ctx = new TestSpringContext();
        ctx.setName("replayTest");

        JobRunner<TestSpringContext> runner = new JobBuilder<TestSpringContext>()
                .enableReplay()
                .node(SpringJob1.class)
                .node("transform", (Function<TestSpringContext, String>) c -> {
                    String upstream = c.getResult(SpringJob1.class);
                    return "transformed_" + upstream;
                })
                .depend(SpringJob1.class)
                .run(ctx);

        DagFlowReplay replay = runner.getReplayRecord();
        assertNotNull(replay);
        assertEquals(2, replay.getNodeCount());
        assertTrue(replay.isSuccess());

        // Store the replay record
        replayStore.put(replay);

        // Verify list shows the record
        String listHtml = replayController.list();
        assertTrue(listHtml.contains(replay.getId()));

        // Verify detail renders HTML waterfall
        ResponseEntity<String> detailResponse = replayController.detail(replay.getId());
        assertEquals(200, detailResponse.getStatusCode().value());
        String detailHtml = detailResponse.getBody();
        assertNotNull(detailHtml);
        assertTrue(detailHtml.contains("Waterfall Timeline"));
        assertTrue(detailHtml.contains("springJob1"));

        // Verify text endpoint
        ResponseEntity<String> textResponse = replayController.text(replay.getId());
        assertEquals(200, textResponse.getStatusCode().value());
        assertTrue(textResponse.getBody().contains("DAG Execution"));

        // Verify JSON API list
        List<DagFlowReplayController.ReplaySummary> apiList = replayController.apiList();
        assertFalse(apiList.isEmpty());
        assertTrue(apiList.stream().anyMatch(s -> s.id().equals(replay.getId())));

        // Verify JSON API detail
        ResponseEntity<DagFlowReplay> apiDetail = replayController.apiDetail(replay.getId());
        assertEquals(200, apiDetail.getStatusCode().value());
        assertEquals(replay.getId(), apiDetail.getBody().getId());
    }

    @Test
    void replayCacheEvictsOldRecords() throws Exception {
        replayStore.clear();

        // Store has capacity 10, fill it with 12 records
        for (int i = 0; i < 12; i++) {
            TestSpringContext ctx = new TestSpringContext();
            ctx.setName("evict_" + i);
            JobRunner<TestSpringContext> runner = new JobBuilder<TestSpringContext>()
                    .enableReplay()
                    .node("job_" + i, (Function<TestSpringContext, String>) c -> "result")
                    .run(ctx);
            replayStore.put(runner.getReplayRecord());
        }

        // Max 10 should be retained
        assertTrue(replayStore.size() <= 10);
    }
}
