package com.github.sofn.jobrunner;

import com.github.sofn.jobrunner.annnotation.JobDepend;
import com.github.sofn.jobrunner.utils.AnnotationUtil;
import com.github.sofn.jobrunner.utils.CycleDependException;
import com.github.sofn.jobrunner.utils.JobRunnerException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * @author lishaofeng
 * @version 1.0 Created at: 2017-03-21 22:21
 */
public class JobRunner {
    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private Map<String, JobState<?>> jobStates = new ConcurrentHashMap<>();
    private Map<String, JobCommand<?>> allJobs = new ConcurrentHashMap<>();
    private Map<String, Object> results = new ConcurrentHashMap<>(); //保存结果

    public <T> JobCommand<T> putJob(JobCommand<T> job) {
        return putJob(getDefaultJobName(job), job);
    }

    public <T> JobCommand<T> putJob(String jobName, JobCommand<T> job) {
        JobCommand<?> preJob = allJobs.get(jobName);
        if (preJob != null && preJob != job) { //如果任务已存在，直接报错
            throw new JobRunnerException("job " + jobName + " already exist!");
        }
        job.setJobName(jobName);
        job.setRunner(this);
        allJobs.put(jobName, job);
        return job;
    }

    public JobCommand<?> getJob(Class clazz) {
        return getJob(getDefaultJobName(clazz));
    }

    public JobCommand<?> getJob(String jobName) {
        return allJobs.get(jobName);
    }

    public JobCommand<?> getEnsureExist(Object clazz) {
        return getEnsureExist(getDefaultJobName(clazz));
    }

    public JobCommand<?> getEnsureExist(String jobName) {
        JobCommand<?> job = getJob(jobName);
        if (job == null) {
            throw new JobRunnerException("depend job " + jobName + " not regist");
        }
        return job;
    }

    public static String getDefaultJobName(Object obj) {
        String className;
        if (obj instanceof Class) {
            className = ((Class) obj).getSimpleName();
        } else if (obj instanceof String) {
            className = (String) obj;
        } else {
            className = obj.getClass().getSimpleName();
        }
        return StringUtils.lowerCase(StringUtils.substring(className, 0, 1))
                + StringUtils.substring(className, 1);
    }

    public <T> T get(JobCommand<T> r) {
        return get(r.getJobName());
    }

    public <T> T get(Class<? extends JobCommand<T>> clazz) {
        return get(getDefaultJobName(clazz));
    }

    /**
     * clazz参数只为获取泛型参数
     */
    public <T> T get(String jobName, Class<? extends JobCommand<T>> clazz) {
        return get(jobName);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String jobName) {
        JobCommand<T> job = (JobCommand<T>) getJob(jobName);
        if (job == null) {
            throw new JobRunnerException("job " + jobName + " not regist");
        }

        Object res = results.get(jobName);
        if (res != null) {
            return (T) res;
        }

        try {
            doJob(jobName, job);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return (T) results.get(jobName);
    }

    /**
     * 启动任务
     */
    public void startJobs() {
        for (JobCommand<?> jobCommand : allJobs.values()) {
            jobCommand.delayCheck();
        }
        resolveDepends();
        checkCycle();
        allJobs.values().stream().filter(job -> !job.isDelay()).forEach(this::queueJob);
    }

    private void resolveDepends() {
        for (Map.Entry<String, JobCommand<?>> entry : allJobs.entrySet()) {
            JobCommand<?> job = entry.getValue();

            for (JobDepend annotation : AnnotationUtil.dependAnnotations(job)) {
                String annoName = annotation.jobName();
                Class<? extends JobCommand> jobClass = annotation.value();
                if (StringUtils.equals(annoName, "")) {
                    job.addDepend(jobClass);
                } else {
                    JobCommand<?> existJob = getJob(annoName);
                    if (existJob == null) {
                        throw new JobRunnerException("depend job: " + annoName + " not regist");
                    } else if (existJob.getClass() != jobClass) {
                        throw new JobRunnerException("depend job: " + annoName + " type is " + existJob.getClass() + " but require " + jobClass);
                    }
                    job.addDepend(annoName);
                }
            }

        }
    }

    /**
     * 检测循环依赖
     */
    private void checkCycle() {
        Set<String> checked = new HashSet<>();
        allJobs.keySet().forEach(job -> checkCycle(job, new ArrayList<>(), checked));
    }

    /**
     * 检测循环依赖
     * 算法：当前节点的依赖树中，没有出现当前节点
     */
    @SuppressWarnings("unchecked")
    private void checkCycle(String job, ArrayList<String> link, Set<String> checked) {
        if (link.contains(job)) {
            StringBuilder builder = new StringBuilder();
            link.forEach(s -> builder.append(s).append(" -> "));
            builder.append(job);
            throw new CycleDependException("cycle depend: " + builder.toString());
        }
        if (checked.contains(job)) {
            return;
        }
        checked.add(job);

        Set<String> dependencys = getEnsureExist(job).getDependencys();
        if (dependencys.size() > 0) {
            link.add(job);
            dependencys.forEach(depend -> checkCycle(depend, (ArrayList<String>) link.clone(), checked));
        }
    }

    /**
     * 启动任务及其依赖, 如果任务已经启动, 返回对应的jobState
     */
    public <T> JobState<T> queueJob(JobCommand<T> job) {
        return queueJob(job.getJobName(), job);
    }

    @SuppressWarnings("unchecked")
    public <T> JobState<T> queueJob(String jobName) {
        JobCommand<T> job = (JobCommand<T>) getJob(jobName);
        if (job == null) {
            throw new JobRunnerException("job " + jobName + " not exist");
        }
        return queueJob(jobName, job);
    }

    @SuppressWarnings("unchecked")
    public <T> JobState<T> queueJob(String jobName, JobCommand<T> job) {
        if (this.getJob(jobName) == null) {
            putJob(jobName, job);
        }
        log.debug("queueJob " + jobName);
        JobState<T> jobState = (JobState<T>) jobStates.computeIfAbsent(job.getJobName(), name -> new JobState<>(this, job));
        if (!jobState.isStarted()) {
            jobState.start();
        }
        return jobState;
    }

    /**
     * 同步执行任务
     */
    public <T> T doJob(JobCommand<T> job) throws InterruptedException, ExecutionException {
        return doJob(getDefaultJobName(job), job);
    }

    /**
     * 同步执行任务
     */
    public <T> T doJob(String jobName, JobCommand<T> job) throws InterruptedException, ExecutionException {
        if (this.getJob(jobName) == null) {
            putJob(jobName, job);
        }

        final JobState<T> state = queueJob(job);

        // 阻塞执行任务
        T result;
        try {
            result = state.getObservable().doOnError(throwable -> state.setDone()).toBlocking().toFuture().get();
            if (result != null) {
                this.results.put(jobName, result);
            } else {
                log.error("JobRunner job return null, jobName: " + jobName);
            }
        } finally {
            state.setDone(); //设置完成状态
        }
        return result;
    }


}
