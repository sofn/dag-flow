package com.github.sofn.dagrunner.annnotation;

import com.github.sofn.dagrunner.JobCommand;

import java.lang.annotation.*;

/**
 * Authors: sofn
 * Version: 1.0  Created at 2017-03-28 23:03.
 */
@Inherited
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobDepend {

    /**
     * 任务名称
     */
    String jobName() default "";

    /**
     * 任务类名
     */
    Class<? extends JobCommand<?>> value();

}
