package com.xihua.mes.common.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Turns on {@link DataScopeInnerInterceptor} for a mapper or service method.
 * Methods without this annotation are not data-scoped. {@code alias} limits the predicate
 * to that table alias when a statement joins several tables.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    String alias() default "";
}
