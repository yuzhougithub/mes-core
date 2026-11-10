package com.xihua.mes.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;

/** Foundation password rules: length, at least N of 4 classes, must not contain the username. */
public final class PasswordPolicy {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%*-_";
    /** BCrypt only uses the first 72 bytes; longer passwords are rejected instead of silently truncated. */
    public static final int MAX_UTF8_BYTES = 72;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    public static List<FieldError> violations(String password, String username, SecurityPolicy policy) {
        List<FieldError> errors = new ArrayList<>();
        if (password == null || password.isBlank()) {
            errors.add(new FieldError("password", ErrorCodes.REQUIRED, "密码必填"));
            return errors;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            errors.add(new FieldError("password", ErrorCodes.TOO_LONG, "密码不能超过 72 字节"));
            return errors;
        }
        if (password.codePointCount(0, password.length()) < policy.minLength()) {
            errors.add(new FieldError("password", ErrorCodes.TOO_SHORT,
                    "密码至少 " + policy.minLength() + " 位"));
        }
        int classes = classes(password);
        if (classes < policy.minClasses()) {
            errors.add(new FieldError("password", ErrorCodes.TOO_FEW_CLASSES,
                    "密码须包含大小写字母、数字、符号中的至少 " + policy.minClasses() + " 类"));
        }
        if (containsUsername(password, username)) {
            errors.add(new FieldError("password", ErrorCodes.CONTAINS_USERNAME, "密码不能包含用户名"));
        }
        return errors;
    }

    public static void check(String password, String username, SecurityPolicy policy) {
        check(password, username, policy, "password");
    }

    public static void check(String password, String username, SecurityPolicy policy, String field) {
        List<FieldError> errors = violations(password, username, policy).stream()
                .map(error -> new FieldError(field, error.code(), error.message()))
                .toList();
        if (!errors.isEmpty()) {
            throw ApiException.passwordPolicy(errors);
        }
    }

    /** Random password that satisfies {@code policy} and does not contain {@code username}. */
    public static String generate(String username, SecurityPolicy policy) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = random(Math.max(policy.minLength(), 12));
            if (violations(candidate, username, policy).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not generate a password that satisfies the policy");
    }

    static int classes(String password) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); ) {
            int cp = password.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isUpperCase(cp)) {
                upper = true;
            } else if (Character.isLowerCase(cp)) {
                lower = true;
            } else if (Character.isDigit(cp)) {
                digit = true;
            } else if (!Character.isLetter(cp) && !Character.isWhitespace(cp)) {
                symbol = true;
            }
        }
        int count = 0;
        if (upper) {
            count++;
        }
        if (lower) {
            count++;
        }
        if (digit) {
            count++;
        }
        if (symbol) {
            count++;
        }
        return count;
    }

    private static boolean containsUsername(String password, String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT));
    }

    private static String random(int length) {
        StringBuilder raw = new StringBuilder(length);
        raw.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS)).append(pick(SYMBOLS));
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        while (raw.length() < length) {
            raw.append(pick(all));
        }
        char[] chars = raw.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }
}
