package com.xihua.mes.common;

/**
 * Thrown when business code needs a plant and {@link com.xihua.mes.auth.CurrentContext}
 * has none (e.g. missing {@code X-Plant-Id} in header mode).
 * HTTP mapping: 400 {@value #CODE}.
 */
public class PlantRequiredException extends RuntimeException {

    public static final String CODE = "PLANT_REQUIRED";

    public PlantRequiredException(String message) {
        super(message);
    }
}
