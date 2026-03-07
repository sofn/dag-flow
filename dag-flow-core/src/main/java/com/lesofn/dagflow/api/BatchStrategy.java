package com.lesofn.dagflow.api;

/**
 * 批量命令执行策略
 * <p>
 * - {@link #ALL}: 全部执行完成（默认）
 * - {@link #atLeast(int)}: 至少 N 个执行完成后自动取消剩余任务
 * - {@link #ANY}: 至少 1 个执行完成后自动取消剩余任务（等价于 atLeast(1)）
 *
 * @author sofn
 */
public final class BatchStrategy {

    /**
     * 全部执行完成（默认策略）
     */
    public static final BatchStrategy ALL = new BatchStrategy(0);

    /**
     * 至少 1 个执行完成
     */
    public static final BatchStrategy ANY = new BatchStrategy(1);

    /**
     * 所需最少完成数，0 表示全部
     */
    private final int requiredCount;

    private BatchStrategy(int requiredCount) {
        this.requiredCount = requiredCount;
    }

    /**
     * 至少 N 个执行完成
     *
     * @param n 最少完成数，必须 >= 1
     * @return BatchStrategy
     */
    public static BatchStrategy atLeast(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("requiredCount must be >= 1, got: " + n);
        }
        if (n == 1) {
            return ANY;
        }
        return new BatchStrategy(n);
    }

    /**
     * 是否要求全部完成
     */
    public boolean isAll() {
        return requiredCount == 0;
    }

    public int getRequiredCount() {
        return requiredCount;
    }
}
