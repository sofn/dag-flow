package com.lesofn.dagflow

import com.lesofn.dagflow.api.AsyncCommand
import com.lesofn.dagflow.test1.Test1Context
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.function.Predicate

/**
 * Conditional execution tests: dependIf / dependIfNot.
 */
class ConditionalSpec extends Specification {

    // ======================== Test Commands ========================

    static class CheckVipJob implements AsyncCommand<Test1Context, Boolean> {
        @Override
        Boolean run(Test1Context context) {
            return context.getName() == "vip"
        }
    }

    static class VipDiscountJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "vip_discount_applied"
        }
    }

    static class NormalDiscountJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "normal_discount_applied"
        }
    }

    static class BuildResultJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            def vip = context.getResult(VipDiscountJob.class)
            def normal = context.getResult(NormalDiscountJob.class)
            return "result:" + (vip ?: normal)
        }
    }

    static class DownstreamOfSkippedJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "downstream_executed"
        }
    }

    static class ExceptionConditionTarget implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "should_not_run"
        }
    }

    static class AlwaysTrueJob implements AsyncCommand<Test1Context, Boolean> {
        @Override
        Boolean run(Test1Context context) {
            return true
        }
    }

    static class BranchAJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "branch_a"
        }
    }

    static class BranchBJob implements AsyncCommand<Test1Context, String> {
        @Override
        String run(Test1Context context) {
            return "branch_b"
        }
    }

    // ======================== Tests ========================

    def "dependIf with true condition - node executes"() {
        given:
        def context = new Test1Context(name: "vip")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(VipDiscountJob.class)
                    .dependIf(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .run(context)

        then:
        runner.getResult(VipDiscountJob.class) == "vip_discount_applied"
    }

    def "dependIf with false condition - node is skipped"() {
        given:
        def context = new Test1Context(name: "normal")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(VipDiscountJob.class)
                    .dependIf(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .run(context)

        then:
        runner.getResult(VipDiscountJob.class) == null
    }

    def "dependIfNot with true condition - node is skipped"() {
        given:
        def context = new Test1Context(name: "vip")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(NormalDiscountJob.class)
                    .dependIfNot(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .run(context)

        then:
        runner.getResult(NormalDiscountJob.class) == null
    }

    def "dependIfNot with false condition - node executes"() {
        given:
        def context = new Test1Context(name: "normal")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(NormalDiscountJob.class)
                    .dependIfNot(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .run(context)

        then:
        runner.getResult(NormalDiscountJob.class) == "normal_discount_applied"
    }

    def "full VIP/Normal scenario - only VIP branch executes for VIP user"() {
        given:
        def context = new Test1Context(name: "vip")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(VipDiscountJob.class)
                    .dependIf(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .node(NormalDiscountJob.class)
                    .dependIfNot(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .run(context)

        then:
        runner.getResult(VipDiscountJob.class) == "vip_discount_applied"
        runner.getResult(NormalDiscountJob.class) == null
    }

    def "full VIP/Normal scenario - only Normal branch executes for normal user"() {
        given:
        def context = new Test1Context(name: "normal")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(VipDiscountJob.class)
                    .dependIf(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .node(NormalDiscountJob.class)
                    .dependIfNot(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .run(context)

        then:
        runner.getResult(VipDiscountJob.class) == null
        runner.getResult(NormalDiscountJob.class) == "normal_discount_applied"
    }

    def "skipped node downstream still executes"() {
        given:
        def context = new Test1Context(name: "normal")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(VipDiscountJob.class)
                    .dependIf(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .node(DownstreamOfSkippedJob.class).depend(VipDiscountJob.class)
                .run(context)

        then:
        runner.getResult(VipDiscountJob.class) == null
        runner.getResult(DownstreamOfSkippedJob.class) == "downstream_executed"
    }

    def "condition exception propagates"() {
        given:
        def context = new Test1Context(name: "test")

        when:
        new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(ExceptionConditionTarget.class)
                    .dependIf(CheckVipJob.class, { ctx -> throw new RuntimeException("condition error") } as Predicate)
                .run(context)

        then:
        thrown(ExecutionException)
    }

    def "diamond topology with conditional branches"() {
        given:
        def context = new Test1Context(name: "vip")

        when:
        def runner = new JobBuilder<Test1Context>()
                .node(CheckVipJob.class)
                .node(VipDiscountJob.class)
                    .dependIf(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .node(NormalDiscountJob.class)
                    .dependIfNot(CheckVipJob.class, { ctx -> ctx.getResult(CheckVipJob.class) } as Predicate)
                .node(BuildResultJob.class).depend(VipDiscountJob.class, NormalDiscountJob.class)
                .run(context)

        then:
        runner.getResult(BuildResultJob.class) == "result:vip_discount_applied"
    }
}
