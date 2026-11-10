package com.xihua.mes.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Function permission required to call a controller method.
 * Several codes are combined with AND unless {@link #anyOf()} is true.
 * Super admin and header mode (dev/test, treated as full access) skip the check.
 * {@link #global()} endpoints are superadmin-only: a plant role never satisfies them.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerm {

    String[] value();

    /** When true, any one of {@link #value()} is enough. */
    boolean anyOf() default false;

    /**
     * Global resource write (plants, menu tree, role definition, role-menu grant).
     * Non-superadmins are denied even when they hold {@link #value()}.
     */
    boolean global() default false;
}
