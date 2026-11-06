package com.xihua.mes.auth;

/**
 * Thin façade for request-scoped auth context (PR#5).
 * Business code should read plant / user from here, not from headers or tokens directly.
 * <p>
 * Skeleton: delegates plant id to {@link PlantContext}; optional {@code userId} is set from
 * {@code X-User-Id} only when {@code mes.auth.mode=header}. JWT mode will populate the same
 * API from the token without changing callers.
 */
public final class CurrentContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private CurrentContext() {
    }

    /** @return current plant id, or {@code null} if unset */
    public static String plantId() {
        return PlantContext.get();
    }

    /** @see PlantContext#require() */
    public static String requirePlantId() {
        return PlantContext.require();
    }

    /** @return current user id, or {@code null} if unset */
    public static String userId() {
        return USER_ID.get();
    }

    static void setUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must be a non-blank string");
        }
        USER_ID.set(userId);
    }

    static void clearUserId() {
        USER_ID.remove();
    }

    static void clear() {
        clearUserId();
        PlantContext.clear();
    }
}
