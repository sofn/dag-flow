package com.lesofn.dagflow.resilience4j;

import com.lesofn.dagflow.api.SyncCommand;
import com.lesofn.dagflow.api.context.DagFlowContext;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resilience4j命令包装器，将业务函数包装为带容错能力的DagFlow节点
 * 支持CircuitBreaker、Retry、Bulkhead、RateLimiter、TimeLimiter
 *
 * @author sofn
 */
public class Resilience4jCommand<C extends DagFlowContext, R> implements SyncCommand<C, R> {

    public static final AttributeKey<String> ATTR_DECORATORS = AttributeKey.stringKey("dagflow.resilience4j.decorators");

    private final Function<C, R> function;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private Bulkhead bulkhead;
    private RateLimiter rateLimiter;
    private TimeLimiter timeLimiter;
    private ScheduledExecutorService timeLimiterExecutor;

    public Resilience4jCommand(Function<C, R> function) {
        this.function = function;
    }

    public Resilience4jCommand<C, R> withCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        return this;
    }

    public Resilience4jCommand<C, R> withRetry(Retry retry) {
        this.retry = retry;
        return this;
    }

    public Resilience4jCommand<C, R> withBulkhead(Bulkhead bulkhead) {
        this.bulkhead = bulkhead;
        return this;
    }

    public Resilience4jCommand<C, R> withRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        return this;
    }

    public Resilience4jCommand<C, R> withTimeLimiter(TimeLimiter timeLimiter, ScheduledExecutorService executor) {
        this.timeLimiter = timeLimiter;
        this.timeLimiterExecutor = executor;
        return this;
    }

    @Override
    public R run(C context) throws Exception {
        List<String> decoratorNames = new ArrayList<>();
        if (bulkhead != null) decoratorNames.add("Bulkhead");
        if (rateLimiter != null) decoratorNames.add("RateLimiter");
        if (circuitBreaker != null) decoratorNames.add("CircuitBreaker");
        if (retry != null) decoratorNames.add("Retry");
        if (timeLimiter != null) decoratorNames.add("TimeLimiter");
        Span.current().setAttribute(ATTR_DECORATORS, String.join(",", decoratorNames));

        Supplier<R> supplier = () -> function.apply(context);

        // 按推荐顺序应用装饰器
        if (bulkhead != null) {
            supplier = Bulkhead.decorateSupplier(bulkhead, supplier);
        }
        if (rateLimiter != null) {
            supplier = RateLimiter.decorateSupplier(rateLimiter, supplier);
        }
        if (circuitBreaker != null) {
            supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        }
        if (retry != null) {
            supplier = Retry.decorateSupplier(retry, supplier);
        }

        if (timeLimiter != null && timeLimiterExecutor != null) {
            Supplier<R> finalSupplier = supplier;
            return timeLimiter.executeFutureSupplier(
                    () -> CompletableFuture.supplyAsync(finalSupplier, timeLimiterExecutor));
        }

        return supplier.get();
    }
}
