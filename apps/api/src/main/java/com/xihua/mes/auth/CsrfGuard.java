package com.xihua.mes.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;

/**
 * Refresh and logout are cookie-authenticated. Require a same-origin {@code Origin} or
 * {@code Referer}, or the non-simple header {@code X-Requested-With: XMLHttpRequest}.
 */
public final class CsrfGuard {

    private CsrfGuard() {
    }

    public static void check(HttpServletRequest request) {
        if (originOk(request) || refererOk(request) || xmlHttp(request)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN,
                "刷新与登出需要同源 Origin/Referer，或请求头 X-Requested-With: XMLHttpRequest", java.util.List.of());
    }

    private static boolean xmlHttp(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private static boolean originOk(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return false;
        }
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return false;
        }
        return origin.equals(request.getScheme() + "://" + host);
    }

    private static boolean refererOk(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return false;
        }
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return false;
        }
        String prefix = request.getScheme() + "://" + host + "/";
        String exact = request.getScheme() + "://" + host;
        return referer.equals(exact) || referer.startsWith(prefix);
    }
}
