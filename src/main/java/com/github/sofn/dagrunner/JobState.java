package com.github.sofn.dagrunner;

import com.github.sofn.dagrunner.annnotation.DagDepend;
import com.github.sofn.dagrunner.utils.AnnotationUtil;
import com.github.sofn.dagrunner.utils.DagRunnerException;
import org.apache.commons.lang.StringUtils;
import rx.Observable;
import rx.subjects.AsyncSubject;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author sofn
 * @version 1.0 Created at: 2017-03-21 22:25
 */
public class JobState<R> {
    private JobCommand<R> job;
    private Observable<R> observable;

    /**
     * 任务是否已经执行
     */
    private final AtomicBoolean started = new AtomicBoolean(false);
    /**
     * 任务是否已经执行完成
     */
    private final AtomicBoolean done = new AtomicBoolean(false);
    private DagRunner runner;

    public JobState(DagRunner runner, JobCommand<R> job) {
        this.runner = runner;
        this.job = job;
    }

    public boolean isStarted() {
        return started.get();
    }

    public void setDone() {
        this.done.set(true);
    }

    public boolean isDone() {
        return done.get();
    }

    @SuppressWarnings("unchecked")
    public void start() {
        if (this.job.getDependencys().isEmpty()) {
            AsyncSubject subject = AsyncSubject.create();
            this.job.toObservable().subscribe(subject);
            this.observable = subject;
        } else {
            List<JobState<?>> jobStates = job.getDependencys().stream()
                    .map(depend -> runner.queueJob(depend))
                    .filter(state -> !state.isDone()).collect(Collectors.toList());

            List<Observable<?>> observables = jobStates.stream()
                    .map(JobState::getObservable)
                    .collect(Collectors.toList());

            AsyncSubject subject = AsyncSubject.create();
            Observable.mergeDelayError(observables)
                    .doOnTerminate(() -> {
                        jobStates.forEach(JobState::setDone);
                        injectValue();
                    })
                    .ignoreElements()
                    .concatWith(job.toObservable())
                    .last().subscribe(subject);
            this.observable = subject;
        }
        this.started.set(true);
    }

    private void injectValue() {
        for (Field field : AnnotationUtil.jobDepends(this.job)) {
            DagDepend annotation = field.getAnnotation(DagDepend.class);
            Class<? extends JobCommand<?>> dependClass = annotation.value();
            try {
                if (StringUtils.equals(annotation.jobName(), "")) {
                    field.set(this.job, runner.get(DagRunner.getDefaultJobName(dependClass)));
                } else {
                    field.set(this.job, runner.get(annotation.jobName()));
                }
            } catch (IllegalAccessException e) {
                throw new DagRunnerException("injectValue error", e);
            }
        }
    }

    public Observable<R> getObservable() {
        return this.observable;
    }
}
