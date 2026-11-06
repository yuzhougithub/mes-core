package com.xihua.mes.common;

/**
 * Thin JSON error body: {@code { "code": "...", "message": "..." }}.
 */
public record ApiError(String code, String message) {
}
