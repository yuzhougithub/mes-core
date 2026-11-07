package com.xihua.mes.routing.app;

import java.util.Optional;
import java.util.UUID;

/** Read-only operation lookups (BOM lines will use this). */
public interface OperationQuery {

    record OperationRef(UUID id, UUID plantId, boolean enabled) {
    }

    Optional<OperationRef> findVisible(UUID operationId);
}
