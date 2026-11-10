package com.xihua.mes.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Function permission required to call a controller method.
 * Several codes are combined with AND unless {@link #anyOf()} is true.
 * Super admin and header mode (dev/test, treated as full access) skip the check.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerm {

    String[] value();

    /** When true, any one of {@link #value()} is enough. */
    boolean anyOf() default false;
}
