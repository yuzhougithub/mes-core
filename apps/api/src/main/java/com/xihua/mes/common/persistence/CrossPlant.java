package com.xihua.mes.common.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a query whose caller has data scope {@code ALL} (or is a super admin), skip the current-plant
 * predicate. {@code ALL} is then limited to plants where the person has an active assignment.
 * A super admin is not limited to assignments. Other scopes keep the current-plant predicate.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossPlant {
}
