package com.xihua.mes.auth;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.xihua.mes.auth.domain.SysUser;
import com.xihua.mes.auth.infra.RevokedJtiMapper;
import com.xihua.mes.auth.infra.SysUserMapper;
import com.xihua.mes.common.PlantHeaderInvalidException;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.org.app.PersonAccountService;

/**
 * Fail-closed access-token check for {@code jwt} mode: signature, expiry, logout, account state,
 * and a still-valid assignment at {@code plt}. A client {@code X-Plant-Id} must match {@code plt}.
 */
@Service
public class AccessAuthenticator {

    private final JwtService jwt;
    private final SysUserMapper users;
    private final RevokedJtiMapper revoked;
    private final PersonAccountService accounts;
    private final PlantDirectory plants;
    private final SecurityPolicyService policies;
    private final Clock clock;

    public AccessAuthenticator(
            JwtService jwt,
            SysUserMapper users,
            RevokedJtiMapper revoked,
            PersonAccountService accounts,
            PlantDirectory plants,
            SecurityPolicyService policies,
            Clock clock) {
        this.jwt = jwt;
        this.users = users;
        this.revoked = revoked;
        this.accounts = accounts;
        this.plants = plants;
        this.policies = policies;
        this.clock = clock;
    }

    public Session authenticate(HttpServletRequest request) {
        String headerPlant = plantHeader(request);
        String token = bearer(request);
        JwtService.AccessClaims claims = jwt.parse(token);
        if (headerPlant != null) {
            UUID headerId = parsePlant(headerPlant);
            if (!headerId.equals(claims.plantId())) {
                throw ApiException.plantMismatch();
            }
        }
        SysUser user = users.selectById(claims.userId());
        if (user == null || !user.getPersonId().equals(claims.personId())) {
            throw ApiException.tokenInvalid();
        }
        if (!"ACTIVE".equals(user.getStatus()) || !accounts.enabled(user.getPersonId())) {
            throw ApiException.accountDisabled();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().toInstant().isAfter(clock.instant())) {
            throw ApiException.accountLocked();
        }
        if (user.getSessionInvalidBefore() != null
                && claims.issuedAt() < user.getSessionInvalidBefore().toEpochSecond()) {
            throw ApiException.tokenInvalid();
        }
        if (revoked.exists(claims.jti())) {
            throw ApiException.tokenInvalid();
        }
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePwd())
                || policies.passwordExpired(user.getPwdChangedAt());
        if (mustChange && !RequestPaths.changePassword(request)) {
            throw ApiException.passwordChangeRequired();
        }
        if (!plants.isActive(claims.plantId())) {
            throw ApiException.plantForbidden("工厂不存在或已停用：" + claims.plantId());
        }
        if (!accounts.hasActive(user.getPersonId(), claims.plantId())) {
            throw ApiException.noPlantAssignment();
        }
        return new Session(user.getId(), user.getPersonId(), claims.plantId(), claims.jti());
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            throw ApiException.unauthenticated("未登录");
        }
        if (!header.startsWith("Bearer ")) {
            throw ApiException.tokenInvalid();
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw ApiException.tokenInvalid();
        }
        return token;
    }

    private static String plantHeader(HttpServletRequest request) {
        List<String> values = headerValues(request);
        if (values.size() > 1) {
            throw headerInvalid("X-Plant-Id must not be supplied more than once");
        }
        if (values.isEmpty() || values.get(0) == null) {
            return null;
        }
        if (values.get(0).isBlank()) {
            throw headerInvalid("X-Plant-Id must be a non-blank string");
        }
        return values.get(0).trim();
    }

    private static UUID parsePlant(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw headerInvalid("X-Plant-Id must be a UUID");
        }
    }

    private static ApiException headerInvalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, PlantHeaderInvalidException.CODE, detail, List.of());
    }

    private static List<String> headerValues(HttpServletRequest request) {
        Enumeration<String> enumeration = request.getHeaders(PlantContextFilter.HEADER_PLANT_ID);
        if (enumeration == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            values.add(enumeration.nextElement());
        }
        return values;
    }

    public record Session(UUID userId, UUID personId, UUID plantId, UUID jti) {
    }
}
