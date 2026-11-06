package com.xihua.mes.auth;

/**
 * Request-scoped auth context façade (PR#5).
 * <p>
 * <b>Business entry point:</b> use {@link #requirePlantId()} / {@link #plantId()} / {@link #userId()}.
 * Do not read request headers or tokens in domain code. Underlying plant storage is
 * {@link PlantContext} (ThreadLocal); JWT will populate the same façade without changing callers.
 * <p>
 * Skeleton: optional {@code userId} from {@code X-User-Id} only when {@code mes.auth.mode=header}.
 */
public final class CurrentContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private CurrentContext() {
    }

    /** @return current plant id, or {@code null} if unset */
    public static String plantId() {
        return PlantContext.get();
    }

    /**
     * Required plant for business operations.
     *
     * @throws com.xihua.mes.common.PlantRequiredException if unset
     */
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
