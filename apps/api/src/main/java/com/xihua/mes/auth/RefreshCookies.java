package com.xihua.mes.auth;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;

/** Refresh token cookie: HttpOnly, Secure, SameSite=Strict, path {@code /api/v1/auth}. */
public final class RefreshCookies {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/v1/auth";

    private RefreshCookies() {
    }

    public static ResponseCookie issue(String raw, Duration ttl) {
        return base(raw).maxAge(ttl).build();
    }

    public static ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH);
    }
}
