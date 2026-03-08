package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.api.CalcCommand
import com.lesofn.dagflow.api.SyncCommand
import com.lesofn.dagflow.api.context.DagFlowContext
import com.lesofn.dagflow.model.DagNodeFactory
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Function

/**
 * node() auto-naming tests
 */
class NodeAutoNamingSpec extends Specification {

    // ======================== Test Context ========================

    static class Ctx extends DagFlowContext {
        String name
        Map<String, Object> data = new ConcurrentHashMap<>()
    }

    // ======================== Test Commands ========================

    static class FetchOrder implements AsyncCommand<Ctx, String> {
        @Override
        String run(Ctx context) { return "order_data" }
    }

    static class CalcDiscount implements CalcCommand<Ctx, Integer> {
        @Override
        Integer run(Ctx context) { return 42 }
    }

    static class SyncJob implements SyncCommand<Ctx, String> {
        @Override
        String run(Ctx context) { return "sync_result" }
    }

    // ======================== Helper ========================

    private static Function<Ctx, ?> func(Closure<?> c) {
        return { Ctx ctx -> c.call(ctx) } as Function<Ctx, ?>
    }

    // ======================== Class-based auto-naming ========================

    // node(Class) auto-generates name className#0
    def "class-based node gets auto-name with #0 suffix"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(FetchOrder.class)
                .run(ctx)

        then:
        runner.getResult(FetchOrder.class) == "order_data"
        runner.getResult("fetchOrder#0") == "order_data"
    }

    // multiple class-based nodes get incrementing suffixes
    def "multiple nodes of same class get incrementing suffixes"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(FetchOrder.class)
                .node(FetchOrder.class)
                .run(ctx)

        then:
        // Both nodes should have results
        runner.getResult("fetchOrder#0") == "order_data"
        runner.getResult("fetchOrder#1") == "order_data"
        // getResult(Class) returns the first one
        runner.getResult(FetchOrder.class) == "order_data"
    }

    // different class-based nodes each get their own #0 suffix
    def "different classes each start from #0"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(FetchOrder.class)
                .node(CalcDiscount.class)
                .node(SyncJob.class)
                .run(ctx)

        then:
        runner.getResult("fetchOrder#0") == "order_data"
        runner.getResult("calcDiscount#0") == 42
        runner.getResult("syncJob#0") == "sync_result"
        // Class-based lookup
        runner.getResult(FetchOrder.class) == "order_data"
        runner.getResult(CalcDiscount.class) == 42
        runner.getResult(SyncJob.class) == "sync_result"
    }

    // explicit name overrides auto-naming
    def "explicit name bypasses auto-naming"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node("myOrder", FetchOrder.class)
                .run(ctx)

        then:
        runner.getResult("myOrder") == "order_data"
        // Class-based lookup also works
        runner.getResult(FetchOrder.class) == "order_data"
    }

    // ======================== Function-based auto-naming ========================

    // node(Function) auto-generates name node#0
    def "function node gets auto-name node#0"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(func { c -> "func_result" })
                .run(ctx)

        then:
        runner.getResult("node#0") == "func_result"
    }

    // multiple function nodes get incrementing suffixes
    def "multiple function nodes get incrementing suffixes"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(func { c -> "first" })
                .node(func { c -> "second" })
                .node(func { c -> "third" })
                .run(ctx)

        then:
        runner.getResult("node#0") == "first"
        runner.getResult("node#1") == "second"
        runner.getResult("node#2") == "third"
    }

    // Consumer node shares the same node# counter with Function nodes
    def "consumer and function nodes share the same counter"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(func { c -> "func_first" })
                .node({ c -> } as Consumer)
                .node(func { c -> "func_third" })
                .run(ctx)

        then:
        runner.getResult("node#0") == "func_first"
        runner.getResult("node#1") == null  // consumer returns null
        runner.getResult("node#2") == "func_third"
    }

    // explicit name on function node bypasses auto-naming
    def "explicit name on function node bypasses auto-naming"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node("myFunc", func { c -> "named_result" })
                .run(ctx)

        then:
        runner.getResult("myFunc") == "named_result"
    }

    // ======================== Mixed auto-naming ========================

    // class and function auto-names use separate counters
    def "class and function auto-names use separate counters"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(FetchOrder.class)
                .node(func { c -> "lambda" })
                .node(CalcDiscount.class)
                .node(func { c -> "lambda2" })
                .run(ctx)

        then:
        runner.getResult("fetchOrder#0") == "order_data"
        runner.getResult("node#0") == "lambda"
        runner.getResult("calcDiscount#0") == 42
        runner.getResult("node#1") == "lambda2"
    }

    // ======================== Dependency with auto-naming ========================

    // depend(Class) resolves auto-named nodes correctly
    def "depend by class resolves auto-named upstream node"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(FetchOrder.class)
                .node(CalcDiscount.class).depend(FetchOrder.class)
                .run(ctx)

        then:
        runner.getResult(FetchOrder.class) == "order_data"
        runner.getResult(CalcDiscount.class) == 42
    }

    // depend(Class) auto-creates upstream node when added before it
    def "depend auto-creates upstream node when it does not exist yet"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(CalcDiscount.class).depend(FetchOrder.class)
                .node(FetchOrder.class)
                .run(ctx)

        then:
        // Both nodes should run
        runner.getResult(FetchOrder.class) == "order_data"
        runner.getResult(CalcDiscount.class) == 42
    }

    // depend(String) with auto-named node
    def "depend by name works with auto-generated names"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(FetchOrder.class)
                .node(func { c -> "downstream" }).depend("fetchOrder#0")
                .run(ctx)

        then:
        runner.getResult("fetchOrder#0") == "order_data"
        runner.getResult("node#0") == "downstream"
    }

    // auto-named function node depending on auto-named class node
    def "auto-named function depends on auto-named class node"() {
        given:
        def ctx = new Ctx(name: "test")

        when:
        def runner = new JobBuilder<Ctx>()
                .node(FetchOrder.class)
                .node(func { c -> "processed" }).depend(FetchOrder.class)
                .run(ctx)

        then:
        runner.getResult("fetchOrder#0") == "order_data"
        runner.getResult("node#0") == "processed"
    }

    // ======================== DagNodeFactory unit tests ========================

    // generateAutoName increments correctly
    def "generateAutoName increments counter per prefix"() {
        given:
        def factory = new DagNodeFactory<Ctx>()

        expect:
        factory.generateAutoName("test") == "test#0"
        factory.generateAutoName("test") == "test#1"
        factory.generateAutoName("test") == "test#2"
        factory.generateAutoName("other") == "other#0"
        factory.generateAutoName("test") == "test#3"
    }

    // generateFuncNodeName uses "node" prefix
    def "generateFuncNodeName uses node prefix"() {
        given:
        def factory = new DagNodeFactory<Ctx>()

        expect:
        factory.generateFuncNodeName() == "node#0"
        factory.generateFuncNodeName() == "node#1"
        factory.generateFuncNodeName() == "node#2"
    }

    // getClassNodeName works correctly
    def "getClassNodeName uncapitalizes simple name"() {
        expect:
        DagNodeFactory.getClassNodeName(FetchOrder.class) == "fetchOrder"
        DagNodeFactory.getClassNodeName(CalcDiscount.class) == "calcDiscount"
        DagNodeFactory.getClassNodeName(SyncJob.class) == "syncJob"
    }
}
