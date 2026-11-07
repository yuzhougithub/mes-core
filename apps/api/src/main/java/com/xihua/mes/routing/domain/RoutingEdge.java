package com.xihua.mes.routing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

/** 返工边（子表）：REWORK / RETURN。 */
@TableName(value = "rtg_routing_edge", autoResultMap = true)
public class RoutingEdge {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID routingId;

    private UUID fromStepId;

    private UUID toStepId;

    private String edgeType;

    private UUID defectCodeId;

    private String remark;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRoutingId() {
        return routingId;
    }

    public void setRoutingId(UUID routingId) {
        this.routingId = routingId;
    }

    public UUID getFromStepId() {
        return fromStepId;
    }

    public void setFromStepId(UUID fromStepId) {
        this.fromStepId = fromStepId;
    }

    public UUID getToStepId() {
        return toStepId;
    }

    public void setToStepId(UUID toStepId) {
        this.toStepId = toStepId;
    }

    public String getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    public UUID getDefectCodeId() {
        return defectCodeId;
    }

    public void setDefectCodeId(UUID defectCodeId) {
        this.defectCodeId = defectCodeId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
