package com.lesofn.dagflow

import com.lesofn.dagflow.api.SyncCommand
import com.lesofn.dagflow.test1.*
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * 虚拟线程支持测试
 */
class VirtualThreadSpec extends Specification {

    // useVirtualThreads 开启后，AsyncCommand 在虚拟线程上执行
    def "AsyncCommand runs on virtual thread when useVirtualThreads enabled"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def threadInfo = new ConcurrentHashMap<String, Boolean>()

        when:
        def runner = new JobBuilder<Test1Context>()
                .useVirtualThreads()
                .funcNode("vt_async", { c ->
                    threadInfo.put("isVirtual", Thread.currentThread().isVirtual())
                    return "async_result"
                } as Function)
                .run(request)

        then:
        runner.getResult("vt_async") == "async_result"
        threadInfo.get("isVirtual") == true
    }

    // SyncCommand 仍在调用线程（非虚拟线程）执行
    def "SyncCommand still runs on caller thread with useVirtualThreads"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def callerThread = Thread.currentThread()
        def threadInfo = new ConcurrentHashMap<String, Thread>()

        when:
        def runner = new JobBuilder<Test1Context>()
                .useVirtualThreads()
                .addNode("syncNode", new SyncCommand<Test1Context, String>() {
                    @Override
                    String run(Test1Context context) {
                        threadInfo.put("execThread", Thread.currentThread())
                        return "sync_result"
                    }
                })
                .run(request)

        then:
        runner.getResult("syncNode") == "sync_result"
        // SyncCommand 应该在非虚拟线程上运行（调用线程或者 CompletableFuture 链线程）
        threadInfo.get("execThread") != null
        !threadInfo.get("execThread").isVirtual()
    }

    // 不开启虚拟线程时，使用传统线程池
    def "without useVirtualThreads, platform threads are used"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def threadInfo = new ConcurrentHashMap<String, Boolean>()

        when:
        def runner = new JobBuilder<Test1Context>()
                .funcNode("pt_async", { c ->
                    threadInfo.put("isVirtual", Thread.currentThread().isVirtual())
                    return "result"
                } as Function)
                .run(request)

        then:
        runner.getResult("pt_async") == "result"
        threadInfo.get("isVirtual") == false
    }

    // 虚拟线程下的依赖链执行
    def "dependency chain with virtual threads"() {
        given:
        def request = new Test1Context()
        request.setName("hello")

        when:
        def runner = new JobBuilder<Test1Context>()
                .useVirtualThreads()
                .addNode(Job2.class).depend(Job1.class)
                .addNode(Job3.class).depend(Job2.class)
                .run(request)

        then:
        runner.getResult(Job1.class) instanceof Long
        runner.getResult(Job2.class) == "job2"
        runner.getResult(Job3.class) != null
    }

    // 虚拟线程下的并行执行
    def "parallel execution with virtual threads"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def threadIds = new ConcurrentHashMap<String, Long>()
        def virtualFlags = new ConcurrentHashMap<String, Boolean>()

        when:
        def runner = new JobBuilder<Test1Context>()
                .useVirtualThreads()
                .funcNode("a", { c ->
                    threadIds.put("a", Thread.currentThread().threadId())
                    virtualFlags.put("a", Thread.currentThread().isVirtual())
                    Thread.sleep(50)
                    return "a"
                } as Function)
                .funcNode("b", { c ->
                    threadIds.put("b", Thread.currentThread().threadId())
                    virtualFlags.put("b", Thread.currentThread().isVirtual())
                    Thread.sleep(50)
                    return "b"
                } as Function)
                .run(request)

        then:
        runner.getResult("a") == "a"
        runner.getResult("b") == "b"
        // 都在虚拟线程上
        virtualFlags.get("a") == true
        virtualFlags.get("b") == true
        // a 和 b 在不同虚拟线程上并行
        threadIds.get("a") != threadIds.get("b")
    }

    // Builder 复用时虚拟线程模式保持
    def "builder reuse preserves virtual thread mode"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def builder = new JobBuilder<Test1Context>()
                .useVirtualThreads()
        builder.funcNode("node", { c ->
            return Thread.currentThread().isVirtual()
        } as Function)

        when:
        def run1 = builder.run(request)
        def run2 = builder.run(request)

        then:
        run1.getResult("node") == true
        run2.getResult("node") == true
    }

    // 不开启虚拟线程的 Builder 不影响节点执行
    def "builder without virtual threads still works normally"() {
        given:
        def request = new Test1Context()
        request.setName("hello")

        when:
        def runner = new JobBuilder<Test1Context>()
                .addNode(Job2.class).depend(Job1.class)
                .run(request)

        then:
        runner.getResult(Job1.class) instanceof Long
        runner.getResult(Job2.class) == "job2"
    }

    // 混合 SyncCommand 和 AsyncCommand 在虚拟线程模式下
    def "mixed sync and async commands with virtual threads"() {
        given:
        def request = new Test1Context()
        request.setName("hello")
        def threadInfo = new ConcurrentHashMap<String, Boolean>()

        when:
        def runner = new JobBuilder<Test1Context>()
                .useVirtualThreads()
                .addNode(Job1.class)
                .addNode("syncJob", new SyncCommand<Test1Context, String>() {
                    @Override
                    String run(Test1Context context) {
                        threadInfo.put("sync", Thread.currentThread().isVirtual())
                        return "sync"
                    }
                })
                .funcNode("asyncJob", { c ->
                    threadInfo.put("async", Thread.currentThread().isVirtual())
                    return "async"
                } as Function)
                .depend(Job1.class)
                .run(request)

        then:
        runner.getResult("syncJob") == "sync"
        runner.getResult("asyncJob") == "async"
        // AsyncCommand (funcNode 是 CalcCommand) 在虚拟线程
        threadInfo.get("async") == true
        // SyncCommand 不在虚拟线程
        threadInfo.get("sync") == false
    }
}
