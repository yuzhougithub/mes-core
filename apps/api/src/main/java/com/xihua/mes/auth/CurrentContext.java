package com.xihua.mes.auth;

import com.xihua.mes.common.persistence.DataScopeBridge;
import com.xihua.mes.common.persistence.DataScopeHolder;

/**
 * Request-scoped auth context façade (PR#5).
 * <p>
 * <b>Business entry point:</b> use {@link #requirePlantId()} / {@link #plantId()} / {@link #userId()}.
 * Do not read request headers or tokens in domain code. Underlying plant storage is
 * {@link PlantContext} (ThreadLocal); JWT will populate the same façade without changing callers.
 * <p>
 * Optional {@code userId} is taken from {@code X-User-Id} only when {@code mes.auth.mode=header}
 * (dev/test). Default {@code jwt} does not trust client headers; plant stays unset (fail closed).
 */
public final class CurrentContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> PERSON_ID = new ThreadLocal<>();
    private static final ThreadLocal<java.util.UUID> JTI = new ThreadLocal<>();

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

    /**
     * Required plant as UUID (all plant ids are UUIDs, A1).
     *
     * @throws com.xihua.mes.common.PlantRequiredException if unset
     * @throws com.xihua.mes.common.error.ApiException 403 {@code PLANT_FORBIDDEN} if not a UUID
     */
    public static java.util.UUID requirePlantUuid() {
        String raw = requirePlantId();
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw com.xihua.mes.common.error.ApiException.plantForbidden("工厂不存在：" + raw);
        }
    }

    /** @return current user id as UUID, or {@code null} if unset / not a UUID (audit columns) */
    public static java.util.UUID userUuid() {
        String raw = USER_ID.get();
        if (raw == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** @return current user id, or {@code null} if unset */
    public static String userId() {
        return USER_ID.get();
    }

    /** @return current person id, or {@code null} if unset (header mode does not invent one) */
    public static String personId() {
        return PERSON_ID.get();
    }

    /** @return current person id as UUID, or {@code null} if unset / not a UUID */
    public static java.util.UUID personUuid() {
        return parseUuid(PERSON_ID.get());
    }

    /** @return access-token {@code jti} when this request was authenticated with a JWT */
    public static java.util.UUID jti() {
        return JTI.get();
    }

    static void setUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must be a non-blank string");
        }
        USER_ID.set(userId);
    }

    static void setPersonId(String personId) {
        if (personId == null || personId.isBlank()) {
            PERSON_ID.remove();
            return;
        }
        PERSON_ID.set(personId);
    }

    static void clearUserId() {
        USER_ID.remove();
    }

    static void setJti(java.util.UUID jti) {
        if (jti == null) {
            JTI.remove();
            return;
        }
        JTI.set(jti);
    }

    static void clear() {
        clearUserId();
        PERSON_ID.remove();
        JTI.remove();
        PlantContext.clear();
        DataScopeHolder.clear();
        DataScopeBridge.clear();
    }

    private static java.util.UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
