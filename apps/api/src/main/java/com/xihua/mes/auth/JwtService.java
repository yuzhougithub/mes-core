package com.xihua.mes.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.xihua.mes.common.error.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * HS256 access tokens. Claims are exactly {@code sub}, {@code pid}, {@code plt}, {@code ver},
 * {@code jti}, {@code iat}, {@code exp} — no roles and no permission points.
 */
public final class JwtService {

    public static final Duration IAT_FUTURE_SKEW = Duration.ofSeconds(60);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Clock clock;

    public JwtService(String secret, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String issue(UUID userId, UUID personId, UUID plantId, int version, Duration ttl) {
        if (userId == null || personId == null || plantId == null || version < 0 || ttl == null || ttl.toSeconds() < 1) {
            throw new IllegalArgumentException("access token claims are incomplete");
        }
        long iat = clock.instant().getEpochSecond();
        long exp = iat + ttl.toSeconds();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("sub", userId.toString());
        payload.put("pid", personId.toString());
        payload.put("plt", plantId.toString());
        payload.put("ver", version);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", iat);
        payload.put("exp", exp);
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = b64(JSON.writeValueAsBytes(payload));
        String signingInput = header + "." + body;
        return signingInput + "." + b64(hmac(signingInput));
    }

    public AccessClaims parse(String token) {
        try {
            return parseChecked(token);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ApiException.tokenInvalid();
        }
    }

    private AccessClaims parseChecked(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.tokenInvalid();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw ApiException.tokenInvalid();
        }
        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] actual;
        try {
            actual = B64D.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw ApiException.tokenInvalid();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw ApiException.tokenInvalid();
        }
        JsonNode header = read(parts[0]);
        if (header == null || !"HS256".equals(header.path("alg").asString())) {
            throw ApiException.tokenInvalid();
        }
        JsonNode body = read(parts[1]);
        if (body == null || !body.isObject()) {
            throw ApiException.tokenInvalid();
        }
        UUID sub = requiredUuid(body, "sub");
        UUID pid = requiredUuid(body, "pid");
        UUID plt = requiredUuid(body, "plt");
        UUID jti = requiredUuid(body, "jti");
        if (!body.hasNonNull("ver") || !body.get("ver").isNumber() || body.get("ver").intValue() < 0) {
            throw ApiException.tokenInvalid();
        }
        if (!body.hasNonNull("iat") || !body.get("iat").isNumber()
                || !body.hasNonNull("exp") || !body.get("exp").isNumber()) {
            throw ApiException.tokenInvalid();
        }
        long iat = body.get("iat").longValue();
        long exp = body.get("exp").longValue();
        long now = clock.instant().getEpochSecond();
        if (iat > now + IAT_FUTURE_SKEW.toSeconds()) {
            throw ApiException.tokenInvalid();
        }
        if (exp <= now) {
            throw ApiException.tokenExpired();
        }
        return new AccessClaims(sub, pid, plt, body.get("ver").intValue(), jti, iat, exp);
    }

    private JsonNode read(String part) {
        try {
            return JSON.readTree(B64D.decode(part));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static UUID requiredUuid(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isString()) {
            throw ApiException.tokenInvalid();
        }
        try {
            return UUID.fromString(node.asString());
        } catch (IllegalArgumentException ex) {
            throw ApiException.tokenInvalid();
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }

    private static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    public record AccessClaims(
            UUID userId,
            UUID personId,
            UUID plantId,
            int version,
            UUID jti,
            long issuedAt,
            long expiresAt) {
    }
}
