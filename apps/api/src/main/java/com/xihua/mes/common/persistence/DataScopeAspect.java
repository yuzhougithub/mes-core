package com.xihua.mes.common.persistence;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/** Publishes {@link DataScope} and {@link CrossPlant} to {@link DataScopeHolder} for the interceptors. */
@Aspect
@Component
public class DataScopeAspect {

    @Around("@annotation(com.xihua.mes.common.persistence.DataScope)"
            + " || @annotation(com.xihua.mes.common.persistence.CrossPlant)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        DataScope scope = method.getAnnotation(DataScope.class);
        boolean cross = method.getAnnotation(CrossPlant.class) != null;
        String alias = scope == null || scope.alias().isBlank() ? null : scope.alias();
        DataScopeHolder.push(alias, scope != null, cross);
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeHolder.pop();
        }
    }
}
