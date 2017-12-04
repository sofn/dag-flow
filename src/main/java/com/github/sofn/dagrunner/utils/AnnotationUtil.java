package com.github.sofn.dagrunner.utils;

import com.github.sofn.dagrunner.JobCommand;
import com.github.sofn.dagrunner.annnotation.JobDepend;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Authors: sofn
 * Version: 1.0  Created at 2017-03-30 23:47.
 */
public class AnnotationUtil {
    private static final ConcurrentHashMap<Class<? extends JobCommand>, List<Field>> fieldsCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<? extends JobCommand>, List<JobDepend>> dependsCache = new ConcurrentHashMap<>();

    public static List<JobDepend> dependAnnotations(JobCommand obj) {
        return dependAnnotations(obj.getClass());
    }

    public static List<JobDepend> dependAnnotations(Class<? extends JobCommand> clazz) {
        return dependsCache.computeIfAbsent(clazz, theClazz ->
                jobDepends(clazz).stream()
                        .map(field -> field.getAnnotation(JobDepend.class))
                        .collect(Collectors.toList())
        );
    }

    public static List<Field> jobDepends(JobCommand obj) {
        return jobDepends(obj.getClass());
    }

    public static List<Field> jobDepends(Class<? extends JobCommand> clazz) {
        return fieldsCache.computeIfAbsent(clazz, theClazz ->
                Arrays.stream(theClazz.getDeclaredFields())
                        .filter(field -> field.isAnnotationPresent(JobDepend.class))
                        .map(field -> {
                            field.setAccessible(true);
                            return field;
                        })
                        .collect(Collectors.toList())
        );
    }

}
