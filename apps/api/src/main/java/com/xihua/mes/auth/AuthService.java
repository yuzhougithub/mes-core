package com.xihua.mes.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xihua.mes.auth.api.AuthDtos.MePerson;
import com.xihua.mes.auth.api.AuthDtos.MePlant;
import com.xihua.mes.auth.api.AuthDtos.MeResponse;
import com.xihua.mes.auth.api.AuthDtos.MeUser;
import com.xihua.mes.auth.api.AuthDtos.PlantBrief;
import com.xihua.mes.auth.api.AuthDtos.PlantOption;
import com.xihua.mes.auth.api.AuthDtos.TokenResponse;
import com.xihua.mes.auth.domain.RefreshToken;
import com.xihua.mes.auth.domain.SysUser;
import com.xihua.mes.auth.infra.RefreshTokenMapper;
import com.xihua.mes.auth.infra.RevokedJtiMapper;
import com.xihua.mes.auth.infra.SysUserMapper;
import com.xihua.mes.auth.infra.TokenHashes;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.org.app.PersonAccountService;
import com.xihua.mes.org.app.PersonAccountService.ActivePlant;
import com.xihua.mes.org.domain.Person;

/**
 * Login, refresh rotation, logout, plant switch, and password change.
 * {@link ApiException} does not roll the transaction back: a failed password still persists the
 * failure counter, and a replayed refresh token still revokes its family.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SysUserMapper users;
    private final RefreshTokenMapper refreshTokens;
    private final RevokedJtiMapper revoked;
    private final PersonAccountService accounts;
    private final PlantDirectory plants;
    private final JwtService jwt;
    private final PasswordEncoder encoder;
    private final SecurityPolicyService policies;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            SysUserMapper users,
            RefreshTokenMapper refreshTokens,
            RevokedJtiMapper revoked,
            PersonAccountService accounts,
            PlantDirectory plants,
            JwtService jwt,
            PasswordEncoder encoder,
            SecurityPolicyService policies,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.revoked = revoked;
        this.accounts = accounts;
        this.plants = plants;
        this.jwt = jwt;
        this.encoder = encoder;
        this.policies = policies;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-" + UUID.randomUUID());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession login(String username, String password, ClientInfo client) {
        SecurityPolicy policy = policies.current();
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        SysUser user = normalized.isEmpty() ? null : users.findByUsername(normalized);
        // '!' is not a BCrypt hash and would return immediately. Compare a dummy hash instead.
        boolean unusable = user != null && AdminBootstrap.UNUSABLE_HASH.equals(user.getPasswordHash());
        boolean matches = encoder.matches(password == null ? "" : password,
                user == null || unusable ? dummyHash : user.getPasswordHash());
        if (user == null) {
            log.info("login result=BAD_CREDENTIALS username={}", normalized);
            throw ApiException.badCredentials();
        }
        OffsetDateTime now = now();
        expireLockIfDue(user, now);
        if (!matches) {
            if (usableAccount(user) && !locked(user)) {
                users.registerFailure(user.getId(), policy.maxFailures(), now.plusMinutes(policy.lockMinutes()), now);
            }
            log.info("login result=BAD_CREDENTIALS username={}", normalized);
            throw ApiException.badCredentials();
        }
        if (!usableAccount(user)) {
            log.info("login result=DISABLED username={}", normalized);
            throw ApiException.accountDisabled();
        }
        if (locked(user)) {
            log.info("login result=LOCKED username={}", normalized);
            throw ApiException.accountLocked();
        }
        UUID plantId = choosePlant(user);
        if (plantId == null) {
            if (!Boolean.TRUE.equals(user.getSuperAdmin())) {
                log.info("login result=NO_PLANT username={}", normalized);
                throw ApiException.noPlantAssignment();
            }
            plantId = NoPlant.ID;
        }
        UUID storedPlant = NoPlant.ID.equals(plantId) ? null : plantId;
        users.markLogin(user.getId(), storedPlant, client.ip(), now);
        String refresh = issueRefresh(user.getId(), storedPlant, UUID.randomUUID(), client, now, policy);
        String access = jwt.issue(user.getId(), user.getPersonId(), plantId, version(user),
                Duration.ofMinutes(policy.accessMinutes()));
        log.info("login result=SUCCESS username={} plant={}", normalized, plantId);
        return new IssuedSession(tokenResponse(user, plantId, access, policy), refresh,
                Duration.ofDays(policy.refreshDays()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession refresh(String raw, ClientInfo client) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.unauthenticated("未登录");
        }
        RefreshToken row = refreshTokens.lockByHash(TokenHashes.sha256(raw));
        if (row == null) {
            throw ApiException.tokenInvalid();
        }
        OffsetDateTime now = now();
        if (row.getRevokedAt() != null) {
            refreshTokens.revokeFamily(row.getFamilyId(), now);
            throw ApiException.tokenInvalid();
        }
        if (row.getExpiresAt() == null || !row.getExpiresAt().toInstant().isAfter(clock.instant())) {
            throw ApiException.tokenExpired();
        }
        SysUser user = users.selectById(row.getUserId());
        if (user == null) {
            throw ApiException.tokenInvalid();
        }
        if (!usableAccount(user)) {
            throw ApiException.accountDisabled();
        }
        if (locked(user)) {
            throw ApiException.accountLocked();
        }
        UUID plantId = row.getPlantId();
        if (plantId == null || !plants.isActive(plantId) || !accounts.hasActive(user.getPersonId(), plantId)) {
            plantId = choosePlant(user);
        }
        if (plantId == null) {
            if (!Boolean.TRUE.equals(user.getSuperAdmin())) {
                throw ApiException.noPlantAssignment();
            }
            plantId = NoPlant.ID;
        }
        SecurityPolicy policy = policies.current();
        UUID storedPlant = NoPlant.ID.equals(plantId) ? null : plantId;
        refreshTokens.revokeOne(row.getId(), now);
        String next = issueRefresh(user.getId(), storedPlant, row.getFamilyId(), client, now, policy);
        if (storedPlant != null) {
            users.touchLastPlant(user.getId(), storedPlant, now);
        }
        String access = jwt.issue(user.getId(), user.getPersonId(), plantId, version(user),
                Duration.ofMinutes(policy.accessMinutes()));
        return new IssuedSession(tokenResponse(user, plantId, access, policy), next,
                Duration.ofDays(policy.refreshDays()));
    }

    @Transactional
    public void logout(String refreshRaw, String bearer) {
        OffsetDateTime now = now();
        if (refreshRaw != null && !refreshRaw.isBlank()) {
            RefreshToken row = refreshTokens.lockByHash(TokenHashes.sha256(refreshRaw));
            if (row != null) {
                if (row.getRevokedAt() != null) {
                    refreshTokens.revokeFamily(row.getFamilyId(), now);
                } else {
                    refreshTokens.revokeOne(row.getId(), now);
                }
            }
        }
        if (bearer == null || bearer.isBlank()) {
            return;
        }
        try {
            JwtService.AccessClaims claims = jwt.parse(bearer);
            revoked.insertIgnore(claims.jti(), claims.userId(),
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(claims.expiresAt()), ZoneOffset.UTC));
        } catch (ApiException ignored) {
            // An expired access token can still log out via the refresh cookie.
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse switchPlant(UUID userId, UUID plantId, String refreshRaw) {
        if (plantId == null) {
            throw ApiException.validation(List.of(new FieldError("plantId", ErrorCodes.REQUIRED, "plantId 必填")));
        }
        SysUser user = requireUser(userId);
        if (!plants.isActive(plantId) || !accounts.hasActive(user.getPersonId(), plantId)) {
            throw ApiException.plantForbidden("无该厂任职：" + plantId);
        }
        OffsetDateTime now = now();
        users.touchLastPlant(user.getId(), plantId, now);
        if (refreshRaw != null && !refreshRaw.isBlank()) {
            RefreshToken row = refreshTokens.lockByHash(TokenHashes.sha256(refreshRaw));
            if (row != null && row.getRevokedAt() == null && user.getId().equals(row.getUserId())
                    && row.getExpiresAt() != null && row.getExpiresAt().toInstant().isAfter(clock.instant())) {
                refreshTokens.updatePlant(row.getId(), plantId);
            }
        }
        SecurityPolicy policy = policies.current();
        String access = jwt.issue(user.getId(), user.getPersonId(), plantId, version(user),
                Duration.ofMinutes(policy.accessMinutes()));
        return tokenResponse(user, plantId, access, policy);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(UUID userId, String oldPassword, String newPassword, UUID jti) {
        SysUser user = requireUser(userId);
        SecurityPolicy policy = policies.current();
        OffsetDateTime now = now();
        expireLockIfDue(user, now);
        if (!encoder.matches(oldPassword == null ? "" : oldPassword, user.getPasswordHash())) {
            if (usableAccount(user) && !locked(user)) {
                users.registerFailure(user.getId(), policy.maxFailures(), now.plusMinutes(policy.lockMinutes()), now);
            }
            throw ApiException.badCredentials();
        }
        if (!usableAccount(user)) {
            throw ApiException.accountDisabled();
        }
        if (locked(user)) {
            throw ApiException.accountLocked();
        }
        String next = newPassword == null ? "" : newPassword;
        if (encoder.matches(next, user.getPasswordHash())) {
            throw ApiException.passwordPolicy(List.of(
                    new FieldError("newPassword", ErrorCodes.SAME_AS_OLD, "新密码不能与当前密码相同")));
        }
        PasswordPolicy.check(next, user.getUsername(), policy, "newPassword");
        if (users.changePassword(user.getId(), encoder.encode(next), false, now) != 1) {
            throw ApiException.tokenInvalid();
        }
        refreshTokens.revokeAll(user.getId(), now);
        if (jti != null) {
            revoked.insertIgnore(jti, user.getId(), now.plusMinutes(policy.accessMinutes()));
        }
    }

    public MeResponse me(UUID userId, UUID plantId) {
        SysUser user = requireUser(userId);
        Person person = accounts.require(user.getPersonId());
        List<ActivePlant> active = accounts.activePlants(user.getPersonId());
        boolean unrestricted = plantId != null && !NoPlant.ID.equals(plantId);
        ActivePlant current = unrestricted
                ? active.stream().filter(plant -> plant.plantId().equals(plantId)).findFirst()
                        .orElseThrow(ApiException::noPlantAssignment)
                : null;
        boolean must = Boolean.TRUE.equals(user.getMustChangePwd()) || policies.passwordExpired(user.getPwdChangedAt());
        return new MeResponse(
                new MeUser(user.getId().toString(), user.getUsername(), user.getStatus(),
                        Boolean.TRUE.equals(user.getSuperAdmin()), must, version(user)),
                new MePerson(person.getId(), person.getCode(), person.getName()),
                current == null ? null : new MePlant(current.plantId().toString(), current.code(), current.name()),
                active.stream()
                        .map(plant -> new PlantOption(plant.plantId().toString(), plant.code(), plant.name(),
                                plant.defaultPlant()))
                        .toList(),
                current == null ? List.of() : accounts.roleCodes(user.getPersonId(), plantId),
                List.of(),
                List.of());
    }

    private TokenResponse tokenResponse(SysUser user, UUID plantId, String access, SecurityPolicy policy) {
        boolean must = Boolean.TRUE.equals(user.getMustChangePwd()) || policies.passwordExpired(user.getPwdChangedAt());
        if (plantId == null || NoPlant.ID.equals(plantId)) {
            return new TokenResponse(access, policy.accessMinutes() * 60L, must, null, List.of());
        }
        ActivePlant plant = accounts.activePlants(user.getPersonId()).stream()
                .filter(row -> row.plantId().equals(plantId))
                .findFirst()
                .orElseThrow(ApiException::noPlantAssignment);
        return new TokenResponse(access, policy.accessMinutes() * 60L, must,
                new PlantBrief(plant.plantId(), plant.code(), plant.name()),
                accounts.roleCodes(user.getPersonId(), plantId));
    }

    private UUID choosePlant(SysUser user) {
        List<ActivePlant> active = accounts.activePlants(user.getPersonId());
        if (active.isEmpty()) {
            return null;
        }
        if (contains(active, user.getDefaultPlantId())) {
            return user.getDefaultPlantId();
        }
        for (ActivePlant plant : active) {
            if (plant.defaultPlant()) {
                return plant.plantId();
            }
        }
        if (contains(active, user.getLastPlantId())) {
            return user.getLastPlantId();
        }
        return active.get(0).plantId();
    }

    private static boolean contains(List<ActivePlant> active, UUID plantId) {
        return plantId != null && active.stream().anyMatch(plant -> plant.plantId().equals(plantId));
    }

    private String issueRefresh(
            UUID userId,
            UUID plantId,
            UUID familyId,
            ClientInfo client,
            OffsetDateTime now,
            SecurityPolicy policy) {
        String raw = TokenHashes.newRefreshSecret();
        RefreshToken row = new RefreshToken();
        row.setId(UUID.randomUUID());
        row.setUserId(userId);
        row.setTokenHash(TokenHashes.sha256(raw));
        row.setFamilyId(familyId);
        row.setPlantId(plantId);
        row.setExpiresAt(now.plusDays(policy.refreshDays()));
        row.setIp(client.ip());
        row.setUserAgent(client.userAgent());
        row.setCreatedAt(now);
        refreshTokens.insert(row);
        return raw;
    }

    private void expireLockIfDue(SysUser user, OffsetDateTime now) {
        if (user.getLockedUntil() != null && !user.getLockedUntil().toInstant().isAfter(clock.instant())) {
            users.clearExpiredLock(user.getId(), now);
            user.setFailCount(0);
            user.setLockedUntil(null);
        }
    }

    private boolean locked(SysUser user) {
        return user.getLockedUntil() != null && user.getLockedUntil().toInstant().isAfter(clock.instant());
    }

    private boolean usableAccount(SysUser user) {
        return "ACTIVE".equals(user.getStatus()) && accounts.enabled(user.getPersonId());
    }

    private SysUser requireUser(UUID userId) {
        if (userId == null) {
            throw ApiException.unauthenticated("未登录");
        }
        SysUser user = users.selectById(userId);
        if (user == null) {
            throw ApiException.tokenInvalid();
        }
        return user;
    }

    private static int version(SysUser user) {
        return user.getPermVersion() == null ? 0 : user.getPermVersion();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public record IssuedSession(TokenResponse body, String refreshToken, Duration refreshMaxAge) {
    }
}
