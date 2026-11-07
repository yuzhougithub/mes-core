package com.xihua.mes.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Business error rendered as RFC 9457 Problem Details by
 * {@link com.xihua.mes.common.ApiExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldError> errors;

    public ApiException(HttpStatus status, String code, String detail, List<FieldError> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, what + " 不存在", List.of());
    }

    public static ApiException staleVersion() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.STALE_VERSION,
                "数据已被修改，请刷新后重试", List.of());
    }

    public static ApiException stateConflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.STATE_CONFLICT, detail, List.of());
    }

    public static ApiException duplicateCode(String code) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_CODE,
                "编码已存在：" + code,
                List.of(new FieldError("code", ErrorCodes.DUPLICATE_CODE, "编码已存在：" + code)));
    }

    public static ApiException inUse(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.IN_USE, detail, List.of());
    }

    public static ApiException plantForbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.PLANT_FORBIDDEN, detail, List.of());
    }

    public static ApiException unprocessable(String code, String detail, List<FieldError> errors) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail, errors);
    }

    public static ApiException validation(List<FieldError> errors) {
        return unprocessable(ErrorCodes.VALIDATION_FAILED, errors.size() + " 个字段不合法", errors);
    }
}
