package com.xihua.mes.common;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.xihua.mes.config.form.DynamicFormSchemaException;

/**
 * Maps domain exceptions to RFC 9457 Problem Details ({@code application/problem+json}).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @ExceptionHandler(PlantRequiredException.class)
    public ResponseEntity<ProblemDetail> handlePlantRequired(PlantRequiredException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(),
                PlantRequiredException.CODE, List.of());
    }

    @ExceptionHandler(PlantHeaderInvalidException.class)
    public ResponseEntity<ProblemDetail> handlePlantHeaderInvalid(PlantHeaderInvalidException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(),
                PlantHeaderInvalidException.CODE, List.of());
    }

    @ExceptionHandler(DynamicFormSchemaException.class)
    public ResponseEntity<ProblemDetail> handleDynamicFormSchema(DynamicFormSchemaException ex) {
        List<Map<String, String>> errors = List.of(Map.of(
                "field", ex.getPointer() == null ? "schema" : ex.getPointer(),
                "code", ex.getCode(),
                "message", ex.getMessage() == null ? "" : ex.getMessage()));
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(),
                ex.getCode(), errors);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<?> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
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
