package com.xihua.mes.auth;

import com.xihua.mes.common.PlantNotSetException;

/**
 * Request-scoped current plant id (ThreadLocal).
 * Populated from {@code X-Plant-Id} by {@link PlantContextFilter}; cleared after the request.
 * Skeleton stage: no JWT / no plant table — callers that need a plant use {@link #require()}.
 */
public final class PlantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PlantContext() {
    }

    public static void set(String plantId) {
        CURRENT.set(plantId);
    }

    /** @return current plant id, or {@code null} if unset */
    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * @return non-blank current plant id
     * @throws PlantNotSetException if unset or blank
     */
    public static String require() {
        String plantId = CURRENT.get();
        if (plantId == null || plantId.isBlank()) {
            throw new PlantNotSetException(
                    "Plant context is not set; send a non-blank X-Plant-Id request header");
        }
        return plantId;
    }
}
