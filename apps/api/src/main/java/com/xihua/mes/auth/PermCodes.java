package com.xihua.mes.auth;

/** Permission points used from service code in addition to {@link RequiresPerm}. */
public final class PermCodes {

    /**
     * Write of a shared master-data row ({@code plant_id} null): materials, operations, routings.
     * Granted to {@code ADMIN} only, so {@code EDITOR} stays limited to the current plant.
     */
    public static final String SHARED_WRITE = "md:shared:update";

    private PermCodes() {
    }
}
