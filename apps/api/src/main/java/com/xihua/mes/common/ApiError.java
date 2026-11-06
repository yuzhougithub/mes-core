package com.xihua.mes.common;

/**
 * @deprecated Prefer RFC 9457 {@link org.springframework.http.ProblemDetail} from
 * {@link ApiExceptionHandler}. Kept temporarily so any stray callers compile.
 */
@Deprecated
public record ApiError(String code, String message) {
}
