package com.xihua.mes.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces {@link RequiresPerm} on controller methods. Missing annotation is not a runtime deny. */
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionService permissions;

    public PermissionInterceptor(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            permissions.check(method);
        }
        return true;
    }
}
