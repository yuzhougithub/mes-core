package com.xihua.mes.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@value #HEADER_PLANT_ID}; when present and non-blank, sets {@link PlantContext}.
 * Always clears the context after the request. Missing header does not fail the filter
 * (health / OpenAPI stay reachable); business code uses {@link PlantContext#require()}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PlantContextFilter extends OncePerRequestFilter {

    public static final String HEADER_PLANT_ID = "X-Plant-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String plantId = request.getHeader(HEADER_PLANT_ID);
        if (plantId != null && !plantId.isBlank()) {
            PlantContext.set(plantId.trim());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            PlantContext.clear();
        }
    }
}
