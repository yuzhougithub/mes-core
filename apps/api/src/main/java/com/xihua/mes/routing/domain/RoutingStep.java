package com.xihua.mes.routing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.UUID;

/** 路线节点（子表，随根整体替换）。 */
@TableName(value = "rtg_routing_step", autoResultMap = true)
public class RoutingStep {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID routingId;

    private Integer stepNo;

    private UUID operationId;

    private UUID workCenterId;

    @TableField("is_rework")
    private Boolean isRework;

    private BigDecimal stdTimeSec;

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

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer stepNo) {
        this.stepNo = stepNo;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public UUID getWorkCenterId() {
        return workCenterId;
    }

    public void setWorkCenterId(UUID workCenterId) {
        this.workCenterId = workCenterId;
    }

    public Boolean getIsRework() {
        return isRework;
    }

    public void setIsRework(Boolean isRework) {
        this.isRework = isRework;
    }

    public BigDecimal getStdTimeSec() {
        return stdTimeSec;
    }

    public void setStdTimeSec(BigDecimal stdTimeSec) {
        this.stdTimeSec = stdTimeSec;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
