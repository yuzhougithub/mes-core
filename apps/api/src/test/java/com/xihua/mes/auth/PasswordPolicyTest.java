package com.xihua.mes.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;

class PasswordPolicyTest {

    private final SecurityPolicy policy = SecurityPolicy.DEFAULTS;

    @Test
    void acceptsThreeClassesAndRejectsShortOrUsername() {
        assertTrue(PasswordPolicy.violations("Abcd1234", "someone", policy).isEmpty());
        assertEquals(ErrorCodes.TOO_SHORT,
                PasswordPolicy.violations("Ab1!", "someone", policy).get(0).code());
        assertEquals(ErrorCodes.TOO_FEW_CLASSES,
                PasswordPolicy.violations("abcdefgh", "someone", policy).get(0).code());
        assertEquals(ErrorCodes.CONTAINS_USERNAME,
                PasswordPolicy.violations("Someone1!", "someone", policy).get(0).code());
    }

    @Test
    void generatedPasswordSatisfiesPolicy() {
        String password = PasswordPolicy.generate("worker", policy);
        assertTrue(PasswordPolicy.violations(password, "worker", policy).isEmpty());
    }

    @Test
    void rejectsPasswordsLongerThan72Bytes() {
        String tooLong = "Aa1!" + "b".repeat(69);
        assertEquals(73, tooLong.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals(ErrorCodes.TOO_LONG, PasswordPolicy.violations(tooLong, "worker", policy).get(0).code());
        String max = "Aa1!" + "b".repeat(68);
        assertEquals(72, max.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(PasswordPolicy.violations(max, "worker", policy).isEmpty());
    }

    @Test
    void checkThrowsPasswordPolicy() {
        ApiException ex = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> PasswordPolicy.check("short", "worker", policy));
        assertEquals(ErrorCodes.PASSWORD_POLICY, ex.getCode());
        assertEquals(422, ex.getStatus().value());
    }
}
