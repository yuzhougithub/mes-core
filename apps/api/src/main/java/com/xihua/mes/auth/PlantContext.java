package com.xihua.mes.auth;

import com.xihua.mes.common.PlantRequiredException;

/**
 * Request-scoped current plant id ({@link ThreadLocal}) — low-level storage.
 * <p>
 * <b>Business code should use {@link CurrentContext#requirePlantId()} / {@link CurrentContext#plantId()},
 * not this class directly.</b> {@link PlantContextFilter} and JWT adapters write here;
 * {@link CurrentContext} is the stable façade (PR#5).
 * <p>
 * <b>Async:</b> plain {@link ThreadLocal} does <em>not</em> propagate to child threads.
 * Pass {@code plantId} explicitly or use a Spring {@code TaskDecorator} (not shipped).
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
     * Low-level require. Prefer {@link CurrentContext#requirePlantId()}.
     *
     * @return non-blank current plant id
     * @throws PlantRequiredException if unset or blank
     */
    public static String require() {
        String plantId = CURRENT.get();
        if (plantId == null || plantId.isBlank()) {
            throw new PlantRequiredException(
                    "Plant context is not set; send a non-blank X-Plant-Id request header");
        }
        return plantId;
    }
}
