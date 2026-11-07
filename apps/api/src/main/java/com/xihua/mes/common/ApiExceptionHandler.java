package com.xihua.mes.common;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.config.form.DynamicFormSchemaException;

/**
 * Maps exceptions to RFC 9457 Problem Details ({@code application/problem+json})
 * with extension members {@code code}, {@code errors[] {field, code, message}}, {@code traceId}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @ExceptionHandler(PlantRequiredException.class)
    public ResponseEntity<ProblemDetail> handlePlantRequired(PlantRequiredException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(),
                PlantRequiredException.CODE, List.of(), req);
    }

    @ExceptionHandler(PlantHeaderInvalidException.class)
    public ResponseEntity<ProblemDetail> handlePlantHeaderInvalid(PlantHeaderInvalidException ex,
            HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(),
                PlantHeaderInvalidException.CODE, List.of(), req);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest req) {
        return problem(ex.getStatus(), titleFor(ex.getStatus()), ex.getMessage(), ex.getCode(), ex.getErrors(),
                req);
    }

    /**
     * Schema write-path violations. Codes with their own top-level entry in the spec
     * ({@code SCHEMA_UNKNOWN_KEY}, {@code SCHEMA_TOO_MANY_FIELDS}, {@code PATTERN_*}) are returned as-is;
     * other schema problems use top-level {@code VALIDATION_FAILED} with the detail code in {@code errors[]}.
     */
    @ExceptionHandler(DynamicFormSchemaException.class)
    public ResponseEntity<ProblemDetail> handleDynamicFormSchema(DynamicFormSchemaException ex,
            HttpServletRequest req) {
        List<FieldError> errors = List.of(new FieldError(
                ex.getPointer() == null ? "" : ex.getPointer(),
                ex.getCode(),
                ex.getMessage() == null ? "" : ex.getMessage()));
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "校验失败", ex.getMessage(),
                DynamicFormSchemaException.topLevelCode(ex.getCode()), errors, req);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateKeyException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "Conflict", "唯一约束冲突", ErrorCodes.DUPLICATE_CODE, List.of(), req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex, HttpServletRequest req) {
        String detail = ex instanceof HttpMessageNotReadableException ? "请求体不是合法 JSON 或字段类型不符" : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", detail, ErrorCodes.BAD_REQUEST, List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er) {
            // Spring MVC errors (404 no handler, 405, 415, ...) keep their status
            HttpStatusCode status = er.getStatusCode();
            return problem(status, titleFor(status), er.getBody().getDetail(), codeFor(status), List.of(), req);
        }
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "服务器内部错误",
                ErrorCodes.INTERNAL, List.of(), req);
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCodes.BAD_REQUEST;
            case 404 -> ErrorCodes.NOT_FOUND;
            case 405 -> "METHOD_NOT_ALLOWED";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? ErrorCodes.INTERNAL : "HTTP_" + status.value();
        };
    }

    private static String titleFor(HttpStatusCode status) {
        if (status.value() == 422) {
            return "校验失败";
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved == null ? "Error" : resolved.getReasonPhrase();
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatusCode status,
            String title,
            String detail,
            String code,
            List<?> errors,
            HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        if (req != null) {
            pd.setInstance(java.net.URI.create(req.getRequestURI()));
        }
        pd.setProperty("code", code);
        pd.setProperty("errors", errors == null ? List.of() : errors);
        pd.setProperty("traceId", currentTraceId());
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(pd);
    }

    private static String currentTraceId() {
        String fromMdc = MDC.get("traceId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
