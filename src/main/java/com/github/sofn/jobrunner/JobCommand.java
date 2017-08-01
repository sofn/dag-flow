package com.github.sofn.jobrunner;

import com.github.sofn.jobrunner.utils.JobRunnerException;
import com.netflix.hystrix.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-21 22:22
 */
public abstract class JobCommand<R> extends HystrixCommand<R> {
    protected Logger jobLog = LoggerFactory.getLogger(JobCommand.class);
    private boolean delay;
    private String jobName;
    private Set<String> dependencys = new HashSet<>();
    private Map<String, JobCommand<?>> delayRunCheck = new HashMap<>(); //延迟校验，因为刚开始runner可能还没设置
    protected JobRunner runner;
    private boolean loged;
    private long runTime;

    public JobCommand(HystrixCommandGroupKey group) {
        super(group);
    }

    public JobCommand(String service, int timeout, int poolSize) {
        super(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(service))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(service + "ThreadPool"))
                .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(poolSize))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(timeout))
        );
    }

    public void setRunner(JobRunner runner) {
        if (this.runner != null && this.runner != runner) {
            throw new JobRunnerException("one job cannot input multi runner");
        }

        this.runner = runner;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobName() {
        return StringUtils.isEmpty(this.jobName) ? JobRunner.getDefaultJobName(this) : this.jobName;
    }

    public boolean isDelay() {
        return delay;
    }

    public void setDelay(boolean delay) {
        this.delay = delay;
    }

    public Set<String> getDependencys() {
        return dependencys;
    }

    public JobCommand<R> addDepend(JobCommand<?> depend) {
        return addDepend(JobRunner.getDefaultJobName(depend), depend);
    }

    public JobCommand<R> addDepend(String jobName, JobCommand<?> depend) {
        if (runner != null) {
            JobCommand<?> job = runner.getJob(jobName);
            if (job == null) {
                runner.putJob(jobName, depend);
            }
        } else {
            delayRunCheck.putIfAbsent(jobName, depend);
        }
        return addDepend(jobName);
    }

    public JobCommand<R> addDepend(Class<?> clazz) {
        return addDepend(JobRunner.getDefaultJobName(clazz));
    }

    public JobCommand<R> addDepend(String jobName) {
        if (runner != null) {
            runner.getEnsureExist(jobName);
            this.dependencys.add(jobName);
        } else {
            delayRunCheck.putIfAbsent(jobName, null);
        }
        return this;
    }

    public void delayCheck() {
        for (Map.Entry<String, JobCommand<?>> entry : delayRunCheck.entrySet()) {
            String jobName = entry.getKey();
            JobCommand<?> job = entry.getValue();
            if (job != null) {
                JobCommand<?> existJob = runner.getJob(jobName);
                if (existJob == null) {
                    runner.putJob(jobName, job);
                    job.delayCheck();
                } else if (existJob != job && StringUtils.equals(this.jobName, existJob.jobName)) {
                    throw new JobRunnerException("multi job: " + jobName);
                }
            } else {
                runner.getEnsureExist(jobName);
            }
            this.dependencys.add(jobName);
        }
        this.delayRunCheck.clear();
    }

    protected <T> T depend(Class<? extends JobCommand<T>> clazz) {
        return null;
    }

    @Override
    protected R getFallback() {
        if (!loged) { //超时、队列满等报错
            jobLog.error("job run error " + this.jobName, this.getExecutionException());
        }
        return null;
    }

    abstract protected R call() throws Exception;

    @Override
    protected R run() throws Exception {
        long start = System.currentTimeMillis();
        try {
            return this.call();
        } catch (Exception e) { //业务报错
            jobLog.error("job run error " + this.jobName, e);
            loged = true;
            throw e;
        } finally {
            this.runTime = System.currentTimeMillis() - start;
            jobLog.info("command {} runTime: {}", this.jobName, this.runTime);
        }
    }

    public long getRunTime() {
        return runTime;
    }
}
