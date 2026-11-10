package com.xihua.mes.common.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;

/**
 * Writes RFC 9457 {@code application/problem+json} from a filter, where
 * {@link com.xihua.mes.common.ApiExceptionHandler} does not run.
 */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, ApiException ex)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        HttpStatusCode status = ex.getStatus();
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String instance = request == null ? "" : request.getRequestURI();
        response.getWriter().write(body(status, title(status), ex.getMessage(), ex.getCode(), ex.getErrors(), instance));
        response.flushBuffer();
    }

    private static String title(HttpStatusCode status) {
        if (status.value() == 422) {
            return "校验失败";
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved == null ? "Error" : resolved.getReasonPhrase();
    }

    private static String body(
            HttpStatusCode status,
            String title,
            String detail,
            String code,
            List<FieldError> errors,
            String instance) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"type\":\"about:blank\",\"title\":").append(quote(title));
        json.append(",\"status\":").append(status.value());
        json.append(",\"detail\":").append(quote(detail == null ? "" : detail));
        if (instance != null && !instance.isBlank()) {
            json.append(",\"instance\":").append(quote(instance));
        }
        json.append(",\"code\":").append(quote(code == null ? ErrorCodes.INTERNAL : code));
        json.append(",\"errors\":[");
        if (errors != null) {
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                FieldError error = errors.get(i);
                json.append("{\"field\":").append(quote(error.field()));
                json.append(",\"code\":").append(quote(error.code()));
                json.append(",\"message\":").append(quote(error.message())).append('}');
            }
        }
        json.append("],\"traceId\":").append(quote(traceId())).append('}');
        return json.toString();
    }

    private static String traceId() {
        String fromMdc = MDC.get(TraceIdFilter.MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }
}
