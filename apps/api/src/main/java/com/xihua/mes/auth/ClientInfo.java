package com.xihua.mes.auth;

import jakarta.servlet.http.HttpServletRequest;

/** Remote address and user agent. {@code X-Forwarded-For} is not trusted. */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(clip(request.getRemoteAddr(), 64), clip(request.getHeader("User-Agent"), 512));
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
