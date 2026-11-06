package com.xihua.mes.common.web;

import java.util.UUID;

/** A visible referenced record and its scope ({@code plantId == null} = shared / global). */
public record ScopedRef(UUID id, UUID plantId) {

    /**
     * Cross-plant rule (overview «跨厂引用»): a shared record may only reference shared/global records;
     * a plant record may reference shared/global or its own plant's records.
     */
    public boolean referenceableFrom(UUID ownerPlantId) {
        return plantId == null || plantId.equals(ownerPlantId);
    }
}
