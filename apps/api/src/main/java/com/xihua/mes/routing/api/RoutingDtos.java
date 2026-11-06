package com.xihua.mes.routing.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.xihua.mes.common.web.Scope;

import tools.jackson.databind.JsonNode;

public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record StepInput(Integer stepNo, UUID operationId, UUID workCenterId, Boolean isRework,
            BigDecimal stdTimeSec, String remark) {
    }

    /**
     * Edges reference steps by {@code stepNo}; the server maps them to step ids.
     * {@code MAIN} may be sent and is ignored (not stored). Only {@code REWORK} and {@code RETURN} are persisted.
     */
    public record EdgeInput(Integer fromStepNo, Integer toStepNo, String edgeType, UUID defectCodeId,
            String remark) {
    }

    /**
     * Shared body for {@code POST /routings} and {@code PUT /routings/{id}}. A mutable class (not a record)
     * so that an <em>omitted</em> {@code layout} can be told apart from an explicit {@code null}.
     */
    public static class RoutingWrite {
        private String scope;
        private String code;
        private String versionNo;
        private String name;
        private UUID materialId;
        private String remark;
        private List<StepInput> steps;
        private List<EdgeInput> edges;
        private JsonNode layout;
        private boolean layoutPresent;
        private Integer lockVersion;

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getVersionNo() {
            return versionNo;
        }

        public void setVersionNo(String versionNo) {
            this.versionNo = versionNo;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public UUID getMaterialId() {
            return materialId;
        }

        public void setMaterialId(UUID materialId) {
            this.materialId = materialId;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public List<StepInput> getSteps() {
            return steps;
        }

        public void setSteps(List<StepInput> steps) {
            this.steps = steps;
        }

        public List<EdgeInput> getEdges() {
            return edges;
        }

        public void setEdges(List<EdgeInput> edges) {
            this.edges = edges;
        }

        public JsonNode getLayout() {
            return layout;
        }

        /** Called by Jackson whenever the key is present, including explicit {@code null}. */
        public void setLayout(JsonNode layout) {
            this.layout = layout;
            this.layoutPresent = true;
        }

        /** @return true when the request contained a {@code layout} key */
        public boolean layoutPresent() {
            return layoutPresent;
        }

        /** @return true when {@code layout} was sent as JSON {@code null} */
        public boolean layoutExplicitNull() {
            return layoutPresent && (layout == null || layout.isNull());
        }

        public Integer getLockVersion() {
            return lockVersion;
        }

        public void setLockVersion(Integer lockVersion) {
            this.lockVersion = lockVersion;
        }
    }

    public record StepView(UUID id, int stepNo, UUID operationId, UUID workCenterId, boolean isRework,
            BigDecimal stdTimeSec, String remark) {
    }

    public record EdgeView(UUID id, int fromStepNo, int toStepNo, String edgeType, UUID defectCodeId,
            String remark) {
    }

    public record RoutingView(UUID id, Scope scope, UUID plantId, String code, String versionNo, String name,
            UUID materialId, String status, OffsetDateTime releasedAt, String remark, int lockVersion,
            List<StepView> steps, List<EdgeView> edges, JsonNode layout, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }
}
