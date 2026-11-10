package com.xihua.mes.auth;

/**
 * Password, lockout, and session limits. Values come from {@code sys_config} and are clamped so a
 * bad row cannot weaken the foundation spec defaults (8 / 3 classes / 5 failures / 15 minutes /
 * access 30 minutes / refresh 7 days). {@code maxAgeDays == 0} means the password does not expire.
 */
public record SecurityPolicy(
        int minLength,
        int minClasses,
        int maxAgeDays,
        int maxFailures,
        int lockMinutes,
        int accessMinutes,
        int refreshDays) {

    public static final SecurityPolicy DEFAULTS = new SecurityPolicy(8, 3, 0, 5, 15, 30, 7);

    public SecurityPolicy {
        minLength = clamp(minLength, 8, 128, 8);
        minClasses = clamp(minClasses, 3, 4, 3);
        maxAgeDays = clamp(maxAgeDays, 0, 3650, 0);
        maxFailures = clamp(maxFailures, 1, 5, 5);
        lockMinutes = clamp(lockMinutes, 15, 24 * 60, 15);
        accessMinutes = clamp(accessMinutes, 5, 30, 30);
        refreshDays = clamp(refreshDays, 1, 7, 7);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
