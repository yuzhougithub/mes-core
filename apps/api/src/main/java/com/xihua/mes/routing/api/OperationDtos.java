package com.xihua.mes.routing.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.xihua.mes.common.web.Scope;
import com.xihua.mes.routing.domain.Operation;

public final class OperationDtos {

    private OperationDtos() {
    }

    public record OperationCreate(String scope, String code, String name, String operationType,
            BigDecimal stdTimeSec, Boolean enabled, String remark) {
    }

    public record OperationView(UUID id, Scope scope, UUID plantId, String code, String name, String operationType,
            BigDecimal stdTimeSec, boolean enabled, String remark, int lockVersion, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        public static OperationView of(Operation o) {
            return new OperationView(o.getId(), Scope.of(o.getPlantId()), o.getPlantId(), o.getCode(), o.getName(),
                    o.getOperationType(), o.getStdTimeSec(), Boolean.TRUE.equals(o.getEnabled()), o.getRemark(),
                    o.getLockVersion(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
