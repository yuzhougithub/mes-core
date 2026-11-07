package com.xihua.mes.material.app;

import java.util.Optional;
import java.util.UUID;

/** Read-only material lookups for other packages (e.g. routing). Respects plant visibility. */
public interface MaterialQuery {

    record MaterialRef(UUID id, UUID plantId, String status) {
    }

    /** @return the material if visible in the current plant (shared or own private), else empty */
    Optional<MaterialRef> findVisible(UUID materialId);
}
