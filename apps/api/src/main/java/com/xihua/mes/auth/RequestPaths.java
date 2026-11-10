package com.xihua.mes.auth;

import jakarta.servlet.http.HttpServletRequest;

final class RequestPaths {

    private RequestPaths() {
    }

    static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    /** Login, refresh, and logout do not require an access token. Everything under {@code /api/v1} does. */
    static boolean anonymous(HttpServletRequest request) {
        String path = path(request);
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/refresh".equals(path)
                || "/api/v1/auth/logout".equals(path);
    }

    static boolean changePassword(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/auth/change-password".equals(path(request));
    }
}
