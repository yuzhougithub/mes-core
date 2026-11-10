package com.xihua.mes.auth;

import java.util.Set;

/**
 * Write endpoints that are intentionally not annotated with {@link RequiresPerm}.
 * Login, refresh, and logout are anonymous. Change-password and switch-plant are
 * authenticated session operations with no permission point.
 */
public final class PermAllowlist {

    private static final Set<String> POST = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/change-password",
            "/api/v1/auth/switch-plant");

    private PermAllowlist() {
    }

    public static boolean allows(String method, String path) {
        if (!"POST".equals(method) || path == null) {
            return false;
        }
        String normalized = path;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return POST.contains(normalized);
    }
}
