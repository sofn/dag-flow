package com.lesofn.dagflow.spring.boot.test;

import com.lesofn.dagflow.JobBuilder;
import com.lesofn.dagflow.JobRunner;
import com.lesofn.dagflow.spring.SpringContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 集成测试：验证 dag-flow 在 Spring Boot 环境中正常工作
 *
 * @author sofn
 */
@SpringBootTest(classes = TestApplication.class)
class DagFlowSpringIntegrationTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void springContextHolderIsAutoConfigured() {
        assertTrue(applicationContext.containsBean("springContextHolder"));
        assertNotNull(applicationContext.getBean(SpringContextHolder.class));
    }

    @Test
    void springContextHolderHoldsApplicationContext() {
        assertTrue(SpringContextHolder.isInjectedApplicationContext());
        assertSame(applicationContext, SpringContextHolder.getApplicationContext());
    }

    @Test
    void springBeanCommandsAreRegistered() {
        assertNotNull(applicationContext.getBean(SpringJob1.class));
        assertNotNull(applicationContext.getBean(SpringJob2.class));
    }

    @Test
    void dependSpringBeanResolvesDagNodes() throws Exception {
        TestSpringContext context = new TestSpringContext();
        context.setName("test");

        JobRunner<TestSpringContext> runner = new JobBuilder<TestSpringContext>()
                .addNode(SpringJob1.class)
                .dependSpringBean("springJob2")
                .run(context);

        assertEquals("springJob1_test", runner.getResult(SpringJob1.class));
        assertEquals("springJob2_test", runner.getResult("springJob2"));
    }

    @Test
    void dagWithOnlySpringBeanNodes() throws Exception {
        TestSpringContext context = new TestSpringContext();
        context.setName("hello");

        JobRunner<TestSpringContext> runner = new JobBuilder<TestSpringContext>()
                .funcNode("start", (java.util.function.Function<TestSpringContext, String>) c -> "started")
                .dependSpringBean("springJob1")
                .run(context);

        assertEquals("springJob1_hello", runner.getResult("springJob1"));
        assertEquals("started", runner.getResult("start"));
    }

    @Test
    void mixedSpringBeanAndRegularNodes() throws Exception {
        TestSpringContext context = new TestSpringContext();
        context.setName("mix");

        JobRunner<TestSpringContext> runner = new JobBuilder<TestSpringContext>()
                .addNode(SpringJob1.class)
                .funcNode("transform", (java.util.function.Function<TestSpringContext, String>) c -> {
                    String r = c.getResult(SpringJob1.class);
                    return "transformed_" + r;
                })
                .depend(SpringJob1.class)
                .run(context);

        assertEquals("springJob1_mix", runner.getResult(SpringJob1.class));
        assertEquals("transformed_springJob1_mix", runner.getResult("transform"));
    }
}
