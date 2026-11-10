package com.xihua.mes.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.web.ProblemResponses;

/**
 * In {@code jwt} mode, every {@code /api/v1} request except login / refresh / logout needs a valid
 * access token. Header mode does nothing here; {@link PlantContextFilter} still trusts
 * {@code X-Plant-Id} (dev and test only).
 * <p>
 * Not a {@code @Component}: {@code @WebMvcTest} would otherwise pick up every {@code Filter}.
 * Registered from {@link AuthInfrastructure} in the full application only.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final String authMode;
    private final AccessAuthenticator authenticator;

    public JwtAuthenticationFilter(
            @Value("${mes.auth.mode:jwt}") String authMode,
            AccessAuthenticator authenticator) {
        this.authMode = authMode == null || authMode.isBlank() ? PlantContextFilter.MODE_JWT : authMode.trim();
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (PlantContextFilter.MODE_HEADER.equalsIgnoreCase(authMode) || RequestPaths.anonymous(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AccessAuthenticator.Session session = authenticator.authenticate(request);
            CurrentContext.setUserId(session.userId().toString());
            CurrentContext.setPersonId(session.personId().toString());
            CurrentContext.setJti(session.jti());
            PlantContext.set(session.plantId().toString());
        } catch (ApiException ex) {
            ProblemResponses.write(request, response, ex);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
