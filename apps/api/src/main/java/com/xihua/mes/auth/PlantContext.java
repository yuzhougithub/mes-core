package com.xihua.mes.auth;

import com.xihua.mes.common.PlantNotSetException;

/**
 * Request-scoped current plant id ({@link ThreadLocal}).
 * <p>
 * Populated by {@link PlantContextFilter} in {@code mes.auth.mode=header} skeleton mode;
 * cleared after the request. Prefer {@link CurrentContext} in business code (PR#5).
 * <p>
 * <b>Async:</b> plain {@link ThreadLocal} does <em>not</em> propagate to child threads or
 * reactive/async executors. Callers that hop threads must pass {@code plantId} explicitly
 * or install a Spring {@code TaskDecorator} / wrap the runnable (not implemented in this
 * skeleton).
 */
public final class PlantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PlantContext() {
    }

    /**
     * @param plantId non-blank plant id
     * @throws IllegalArgumentException if {@code plantId} is null or blank
     */
    public static void set(String plantId) {
        if (plantId == null || plantId.isBlank()) {
            throw new IllegalArgumentException("plantId must be a non-blank string");
        }
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
