package com.ankur.instrumentor;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackMetrics {

    /**
     * Category, use it for categorizing various {@link TrackMetrics#filterName()}
     * For example: DynamoDB, Elastic Search.
     * Will default to class name where the annotation was used.
     */
    String category() default "default";

    /**
     * Item name whose metrics are to be tracked
     * Will default to method name over which the annotation was used
     */
    String filterName() default "default";
}