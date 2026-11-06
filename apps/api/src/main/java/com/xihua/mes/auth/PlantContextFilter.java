package com.xihua.mes.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.xihua.mes.common.PlantHeaderInvalidException;

/**
 * Skeleton plant (and optional user) context from request headers.
 * <p>
 * <b>Auth mode:</b> reads {@code X-Plant-Id} / {@code X-User-Id} only when
 * {@code mes.auth.mode=header} (default). Any other value (including future {@code jwt})
 * ignores those headers — JWT must populate {@link CurrentContext} from the token;
 * <b>after JWT lands, never trust a client-supplied {@code X-Plant-Id}</b>.
 * <p>
 * Missing {@code X-Plant-Id} does not fail the filter (health / OpenAPI stay reachable);
 * business code uses {@link CurrentContext#requirePlantId()}.
 * Present-but-blank or multi-value {@code X-Plant-Id} → 400 {@code PLANT_HEADER_INVALID}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PlantContextFilter extends OncePerRequestFilter {

    public static final String HEADER_PLANT_ID = "X-Plant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String MODE_HEADER = "header";

    private static final Logger log = LoggerFactory.getLogger(PlantContextFilter.class);

    private final String authMode;

    public PlantContextFilter(@Value("${mes.auth.mode:header}") String authMode) {
        this.authMode = authMode == null ? MODE_HEADER : authMode.trim();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            if (MODE_HEADER.equalsIgnoreCase(authMode)) {
                applyHeaderMode(request, response);
                if (response.isCommitted()) {
                    return;
                }
            } else if (request.getHeader(HEADER_PLANT_ID) != null) {
                log.debug(
                        "Ignoring {} because mes.auth.mode={} (JWT must not trust client plant header)",
                        HEADER_PLANT_ID,
                        authMode);
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentContext.clear();
        }
    }

    private void applyHeaderMode(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        List<String> plantValues = headerValues(request, HEADER_PLANT_ID);
        if (plantValues.size() > 1) {
            writePlantHeaderInvalid(response, "X-Plant-Id must not be supplied more than once");
            return;
        }
        if (plantValues.size() == 1) {
            String raw = plantValues.get(0);
            if (raw == null || raw.isBlank()) {
                writePlantHeaderInvalid(response, "X-Plant-Id must be a non-blank string");
                return;
            }
            PlantContext.set(raw.trim());
        }

        List<String> userValues = headerValues(request, HEADER_USER_ID);
        if (userValues.size() == 1) {
            String user = userValues.get(0);
            if (user != null && !user.isBlank()) {
                CurrentContext.setUserId(user.trim());
            }
        }
    }

    private static List<String> headerValues(HttpServletRequest request, String name) {
        Enumeration<String> enumeration = request.getHeaders(name);
        if (enumeration == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            values.add(enumeration.nextElement());
        }
        return values;
    }

    private static void writePlantHeaderInvalid(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        String body = "{"
                + "\"type\":\"about:blank\","
                + "\"title\":\"Bad Request\","
                + "\"status\":400,"
                + "\"detail\":" + jsonString(message) + ","
                + "\"code\":\"" + PlantHeaderInvalidException.CODE + "\","
                + "\"errors\":[],"
                + "\"traceId\":\"" + traceId + "\""
                + "}";
        response.getWriter().write(body);
        response.flushBuffer();
    }

    private static String jsonString(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
}
