package com.xihua.mes.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthInfrastructure {

    private static final Logger log = LoggerFactory.getLogger(AuthInfrastructure.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public JwtService jwtService(
            @Value("${mes.auth.jwt.secret:}") String secret,
            @Value("${mes.auth.mode:jwt}") String mode,
            Clock clock) {
        String key = secret == null ? "" : secret;
        if (key.getBytes(StandardCharsets.UTF_8).length < 32) {
            String normalized = mode == null ? "" : mode.trim();
            if (!PlantContextFilter.MODE_HEADER.equalsIgnoreCase(normalized)) {
                throw new IllegalStateException(
                        "mes.auth.jwt.secret must be at least 32 bytes when mes.auth.mode=jwt");
            }
            byte[] buf = new byte[32];
            new SecureRandom().nextBytes(buf);
            key = Base64.getEncoder().encodeToString(buf);
            log.warn("mes.auth.jwt.secret is unset; using an ephemeral secret because mes.auth.mode=header");
        }
        return new JwtService(key, clock);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            @Value("${mes.auth.mode:jwt}") String mode,
            AccessAuthenticator authenticator) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthenticationFilter(mode, authenticator));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
