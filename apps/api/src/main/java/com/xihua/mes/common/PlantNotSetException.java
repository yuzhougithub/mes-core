package com.xihua.mes.common;

/**
 * Thrown when business code calls {@link com.xihua.mes.auth.PlantContext#require()}
 * without a current plant (e.g. missing {@code X-Plant-Id}).
 */
public class PlantNotSetException extends RuntimeException {

    public PlantNotSetException(String message) {
        super(message);
    }
}
