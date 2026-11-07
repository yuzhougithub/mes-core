package com.xihua.mes.common;

/**
 * {@code X-Plant-Id} present but blank, or supplied more than once.
 * Mapped to HTTP 400 {@code PLANT_HEADER_INVALID}.
 */
public class PlantHeaderInvalidException extends RuntimeException {

    public static final String CODE = "PLANT_HEADER_INVALID";

    public PlantHeaderInvalidException(String message) {
        super(message);
    }
}
