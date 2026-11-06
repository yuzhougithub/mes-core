package com.xihua.mes.common;

/**
 * Extension point for MyBatis-Plus {@code plant_id} row-level filtering.
 * <p>
 * Placeholder only — not registered. When domain entities land, implement a MyBatis-Plus
 * {@code InnerInterceptor} (or tenant line handler) that appends
 * {@code plant_id = CurrentContext.requirePlantId()} on plant-scoped SELECT/UPDATE/DELETE,
 * and wire it via {@code MybatisPlusInterceptor}. Shared-scope rows and global tables
 * (e.g. plants) must opt out explicitly.
 */
public final class PlantLineInterceptor {

    private PlantLineInterceptor() {
    }
}
