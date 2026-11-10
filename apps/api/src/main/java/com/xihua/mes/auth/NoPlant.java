package com.xihua.mes.auth;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sentinel plant id for a super admin who has no assignment yet.
 * The access token carries this id; it is not a row in {@code org_plant}.
 */
public final class NoPlant {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private NoPlant() {
    }

    /** Bootstrap session: create a plant, manage users, change password, read {@code /auth/me}. */
    public static boolean allows(HttpServletRequest request) {
        String path = RequestPaths.path(request);
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) && "/api/v1/plants".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/v1/auth/me".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/v1/auth/change-password".equals(path)) {
            return true;
        }
        return "/api/v1/users".equals(path) || path.startsWith("/api/v1/users/");
    }
}
