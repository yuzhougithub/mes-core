package com.xihua.mes.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant ISSUED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void roundTripKeepsClaimsAndRejectsTamperOrExpiry() {
        JwtService issuer = new JwtService(SECRET, Clock.fixed(ISSUED, ZoneOffset.UTC));
        UUID user = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        UUID plant = UUID.randomUUID();
        String token = issuer.issue(user, person, plant, 3, Duration.ofMinutes(30));
        JwtService.AccessClaims claims = issuer.parse(token);
        assertEquals(user, claims.userId());
        assertEquals(person, claims.personId());
        assertEquals(plant, claims.plantId());
        assertEquals(3, claims.version());

        String[] parts = token.split("\\.");
        char[] signature = parts[2].toCharArray();
        signature[0] = signature[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(signature);
        ApiException badSig = assertThrows(ApiException.class, () -> issuer.parse(tampered));
        assertEquals(ErrorCodes.TOKEN_INVALID, badSig.getCode());

        ApiException malformed = assertThrows(ApiException.class, () -> issuer.parse("not-a-jwt"));
        assertEquals(ErrorCodes.TOKEN_INVALID, malformed.getCode());

        String noneHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(ApiException.class, () -> issuer.parse(noneHeader + "." + parts[1] + "."));

        JwtService later = new JwtService(SECRET, Clock.fixed(ISSUED.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
        ApiException expired = assertThrows(ApiException.class, () -> later.parse(token));
        assertEquals(ErrorCodes.TOKEN_EXPIRED, expired.getCode());
    }
}
