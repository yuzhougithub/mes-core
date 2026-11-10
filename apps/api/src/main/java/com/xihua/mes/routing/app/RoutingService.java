package com.xihua.mes.routing.app;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xihua.mes.auth.PermissionService;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.common.web.Scope;
import com.xihua.mes.common.web.ScopedRef;
import com.xihua.mes.config.app.ConfigQuery;
import com.xihua.mes.material.app.MaterialQuery;
import com.xihua.mes.material.domain.VersionStatus;
import com.xihua.mes.org.app.OrgQuery;
import com.xihua.mes.routing.api.RoutingDtos.EdgeInput;
import com.xihua.mes.routing.api.RoutingDtos.EdgeView;
import com.xihua.mes.routing.api.RoutingDtos.RoutingView;
import com.xihua.mes.routing.api.RoutingDtos.RoutingWrite;
import com.xihua.mes.routing.api.RoutingDtos.StepInput;
import com.xihua.mes.routing.api.RoutingDtos.StepView;
import com.xihua.mes.routing.domain.Routing;
import com.xihua.mes.routing.domain.RoutingEdge;
import com.xihua.mes.routing.domain.RoutingLayout;
import com.xihua.mes.routing.domain.RoutingStep;
import com.xihua.mes.routing.infra.RoutingEdgeMapper;
import com.xihua.mes.routing.infra.RoutingMapper;
import com.xihua.mes.routing.infra.RoutingStepMapper;

import tools.jackson.databind.JsonNode;

/**
 * Minimal routing draft save (slice 1): create / read / PUT whole-replace of header + steps + edges + layout.
 * Release / obsolete / copy / effective and the release-time business checks are deferred.
 * <p>
 * Layout rules (A23, architecture «布局 layout»): explicit layout → strict whitelist against <em>this request's</em>
 * steps (422 {@code LAYOUT_INVALID}, never trimmed); omitted → keep stored coordinates but prune orphan keys in the
 * same transaction; explicit {@code null} → clear. Only {@code DRAFT} is writable (409 {@code STATE_CONFLICT}).
 * <p>
 * {@code edgeType=MAIN} is canvas-only: those edges are dropped and not stored, and the save still succeeds.
 * Only {@code REWORK} and {@code RETURN} are persisted. Any other edge type is 422.
 */
@Service
public class RoutingService {

    /** Persisted edge types. {@code MAIN} is not in this set; it is dropped before validation. */
    private static final Set<String> EDGE_TYPES = Set.of("REWORK", "RETURN");
    private static final String EDGE_TYPE_MAIN = "MAIN";

    private final RoutingMapper mapper;
    private final RoutingStepMapper stepMapper;
    private final RoutingEdgeMapper edgeMapper;
    private final OperationQuery operations;
    private final MaterialQuery materials;
    private final OrgQuery org;
    private final ConfigQuery config;
    private final PermissionService permissions;

    public RoutingService(RoutingMapper mapper, RoutingStepMapper stepMapper, RoutingEdgeMapper edgeMapper,
            OperationQuery operations, MaterialQuery materials, OrgQuery org, ConfigQuery config,
            PermissionService permissions) {
        this.mapper = mapper;
        this.stepMapper = stepMapper;
        this.edgeMapper = edgeMapper;
        this.operations = operations;
        this.materials = materials;
        this.org = org;
        this.config = config;
        this.permissions = permissions;
    }

    public RoutingView get(UUID id) {
        return view(load(id));
    }

    @Transactional
    public RoutingView create(RoutingWrite req) {
        Scope scope = Scope.parseRequired(req.getScope());
        UUID plantId = scope.plantIdForCreate();
        permissions.requireSharedMaster(plantId);
        Errors errors = new Errors();
        String code = errors.text("code", req.getCode(), true, 64);
        String versionNo = errors.text("versionNo", req.getVersionNo(), true, 16);
        String name = errors.text("name", req.getName(), true, 200);
        errors.text("remark", req.getRemark(), false, 500);
        checkMaterial(errors, req.getMaterialId(), plantId);
        List<StepInput> steps = req.getSteps() == null ? List.of() : req.getSteps();
        List<EdgeInput> edges = req.getEdges() == null ? List.of() : req.getEdges();
        checkSteps(errors, steps, plantId);
        checkEdges(errors, edges, steps, plantId);
        throwIfAny(errors);
        Set<Integer> stepNos = stepNos(steps);
        JsonNode layout = req.layoutPresent() && !req.layoutExplicitNull()
                ? RoutingLayout.validateExplicit(req.getLayout(), stepNos) : null;
        if (mapper.versionTaken(code, plantId, versionNo)) {
            throw ApiException.duplicateCode(code + "/" + versionNo);
        }
        Routing r = new Routing();
        r.setId(UUID.randomUUID());
        r.setPlantId(plantId);
        r.setCode(code);
        r.setVersionNo(versionNo);
        r.setName(name);
        r.setMaterialId(req.getMaterialId());
        r.setRemark(req.getRemark());
        r.setStatus(VersionStatus.DRAFT.name());
        r.setLayoutJson(layout);
        r.setLockVersion(0);
        r.setDeleted(false);
        mapper.insert(r);
        replaceChildren(r.getId(), steps, edges);
        return get(r.getId());
    }

    /** Whole replacement. Header {@code code}/{@code versionNo}/{@code scope} are immutable. */
    @Transactional
    public RoutingView update(UUID id, RoutingWrite req) {
        Routing r = load(id);
        permissions.requireSharedMaster(r.getPlantId());
        Locks.check(req.getLockVersion(), r.getLockVersion());
        if (!VersionStatus.DRAFT.name().equals(r.getStatus())) {
            throw ApiException.stateConflict("只有草稿工艺路线可修改（当前状态 " + r.getStatus() + "）");
        }
        Errors errors = new Errors();
        if (req.getCode() != null && !req.getCode().equals(r.getCode())) {
            errors.add("code", ErrorCodes.IMMUTABLE, "编码创建后不可改");
        }
        if (req.getVersionNo() != null && !req.getVersionNo().equals(r.getVersionNo())) {
            errors.add("versionNo", ErrorCodes.IMMUTABLE, "版本号创建后不可改");
        }
        if (req.getScope() != null && Scope.parse(req.getScope(), "scope") != Scope.of(r.getPlantId())) {
            errors.add("scope", ErrorCodes.IMMUTABLE, "scope 创建后不可改");
        }
        String name = errors.text("name", req.getName(), true, 200);
        errors.text("remark", req.getRemark(), false, 500);
        checkMaterial(errors, req.getMaterialId(), r.getPlantId());
        List<StepInput> steps = req.getSteps() == null ? List.of() : req.getSteps();
        List<EdgeInput> edges = req.getEdges() == null ? List.of() : req.getEdges();
        checkSteps(errors, steps, r.getPlantId());
        checkEdges(errors, edges, steps, r.getPlantId());
        throwIfAny(errors);

        Set<Integer> stepNos = stepNos(steps);
        JsonNode layout;
        if (!req.layoutPresent()) {
            layout = RoutingLayout.pruneOrphans(r.getLayoutJson(), stepNos);
        } else if (req.layoutExplicitNull()) {
            layout = null;
        } else {
            layout = RoutingLayout.validateExplicit(req.getLayout(), stepNos);
        }

        r.setName(name);
        r.setMaterialId(req.getMaterialId());
        r.setRemark(req.getRemark());
        r.setLayoutJson(layout);
        Locks.requireUpdated(mapper.updateById(r));
        replaceChildren(id, steps, edges);
        return get(id);
    }

    private void replaceChildren(UUID routingId, List<StepInput> steps, List<EdgeInput> edges) {
        edgeMapper.delete(new QueryWrapper<RoutingEdge>().eq("routing_id", routingId));
        stepMapper.delete(new QueryWrapper<RoutingStep>().eq("routing_id", routingId));
        Map<Integer, UUID> idByStepNo = new HashMap<>();
        for (StepInput s : steps) {
            RoutingStep step = new RoutingStep();
            step.setId(UUID.randomUUID());
            step.setRoutingId(routingId);
            step.setStepNo(s.stepNo());
            step.setOperationId(s.operationId());
            step.setWorkCenterId(s.workCenterId());
            step.setIsRework(Boolean.TRUE.equals(s.isRework()));
            step.setStdTimeSec(s.stdTimeSec());
            step.setRemark(s.remark());
            stepMapper.insert(step);
            idByStepNo.put(s.stepNo(), step.getId());
        }
        for (EdgeInput e : edges) {
            if (e != null && EDGE_TYPE_MAIN.equals(e.edgeType())) {
                continue;
            }
            RoutingEdge edge = new RoutingEdge();
            edge.setId(UUID.randomUUID());
            edge.setRoutingId(routingId);
            edge.setFromStepId(idByStepNo.get(e.fromStepNo()));
            edge.setToStepId(idByStepNo.get(e.toStepNo()));
            edge.setEdgeType(e.edgeType());
            edge.setDefectCodeId(e.defectCodeId());
            edge.setRemark(e.remark());
            edgeMapper.insert(edge);
        }
    }

    private void checkMaterial(Errors errors, UUID materialId, UUID ownerPlant) {
        if (materialId == null) {
            return;
        }
        Optional<MaterialQuery.MaterialRef> ref = materials.findVisible(materialId);
        if (ref.isEmpty()) {
            errors.add("materialId", ErrorCodes.NOT_FOUND, "物料不存在");
        } else if (!new ScopedRef(materialId, ref.get().plantId()).referenceableFrom(ownerPlant)) {
            errors.add("materialId", ErrorCodes.CROSS_PLANT_REFERENCE, "共享路线只能引用共享物料");
        }
    }

    private void checkSteps(Errors errors, List<StepInput> steps, UUID ownerPlant) {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            StepInput s = steps.get(i);
            String p = "steps[" + i + "]";
            if (s == null) {
                errors.add(p, ErrorCodes.REQUIRED, "节点不能为空");
                continue;
            }
            if (s.stepNo() == null) {
                errors.add(p + ".stepNo", ErrorCodes.REQUIRED, "stepNo 必填");
            } else if (s.stepNo() <= 0) {
                errors.add(p + ".stepNo", ErrorCodes.OUT_OF_RANGE, "stepNo 须为正整数");
            } else if (!seen.add(s.stepNo())) {
                errors.add(p + ".stepNo", ErrorCodes.DUPLICATE, "stepNo 在整条路线内须唯一（含返工）：" + s.stepNo());
            }
            if (errors.required(p + ".operationId", s.operationId()) != null) {
                Optional<OperationQuery.OperationRef> op = operations.findVisible(s.operationId());
                if (op.isEmpty()) {
                    errors.add(p + ".operationId", ErrorCodes.NOT_FOUND, "工序不存在");
                } else if (!new ScopedRef(op.get().id(), op.get().plantId()).referenceableFrom(ownerPlant)) {
                    errors.add(p + ".operationId", ErrorCodes.CROSS_PLANT_REFERENCE, "共享路线只能引用共享工序");
                }
            }
            if (s.workCenterId() != null) {
                if (ownerPlant == null) {
                    errors.add(p + ".workCenterId", ErrorCodes.CROSS_PLANT_REFERENCE, "共享路线的节点不能填工作中心");
                } else if (!org.workCenterVisible(s.workCenterId())) {
                    errors.add(p + ".workCenterId", ErrorCodes.NOT_FOUND, "工作中心不存在");
                }
            }
            if (s.stdTimeSec() != null && s.stdTimeSec().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(p + ".stdTimeSec", ErrorCodes.OUT_OF_RANGE, "标准工时不能为负");
            }
            errors.text(p + ".remark", s.remark(), false, 500);
        }
    }

    private void checkEdges(Errors errors, List<EdgeInput> edges, List<StepInput> steps, UUID ownerPlant) {
        Set<Integer> stepNos = stepNos(steps);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < edges.size(); i++) {
            EdgeInput e = edges.get(i);
            String p = "edges[" + i + "]";
            if (e == null) {
                errors.add(p, ErrorCodes.REQUIRED, "边不能为空");
                continue;
            }
            // Main sequence is visual-only on the canvas: ignore it, do not 422, do not store it.
            if (EDGE_TYPE_MAIN.equals(e.edgeType())) {
                continue;
            }
            if (errors.required(p + ".fromStepNo", e.fromStepNo()) != null && !stepNos.contains(e.fromStepNo())) {
                errors.add(p + ".fromStepNo", ErrorCodes.NOT_FOUND, "起点不在本路线节点中：" + e.fromStepNo());
            }
            if (errors.required(p + ".toStepNo", e.toStepNo()) != null && !stepNos.contains(e.toStepNo())) {
                errors.add(p + ".toStepNo", ErrorCodes.NOT_FOUND, "终点不在本路线节点中：" + e.toStepNo());
            }
            if (e.fromStepNo() != null && e.fromStepNo().equals(e.toStepNo())) {
                errors.add(p, ErrorCodes.INVALID_VALUE, "起点与终点不能相同");
            }
            if (errors.required(p + ".edgeType", e.edgeType()) != null && !EDGE_TYPES.contains(e.edgeType())) {
                errors.add(p + ".edgeType", ErrorCodes.INVALID_VALUE, "edgeType 只能是 REWORK 或 RETURN");
            }
            if (e.defectCodeId() != null) {
                Optional<ScopedRef> d = config.findDefectCode(e.defectCodeId());
                if (d.isEmpty()) {
                    errors.add(p + ".defectCodeId", ErrorCodes.NOT_FOUND, "不良代码不存在");
                } else if (!d.get().referenceableFrom(ownerPlant)) {
                    errors.add(p + ".defectCodeId", ErrorCodes.CROSS_PLANT_REFERENCE, "共享路线只能引用共享不良代码");
                }
            }
            String key = e.fromStepNo() + ">" + e.toStepNo() + ":" + e.edgeType() + ":" + e.defectCodeId();
            if (!seen.add(key)) {
                errors.add(p, ErrorCodes.DUPLICATE, "重复的边");
            }
        }
    }

    private static Set<Integer> stepNos(List<StepInput> steps) {
        Set<Integer> nos = new LinkedHashSet<>();
        for (StepInput s : steps) {
            if (s != null && s.stepNo() != null) {
                nos.add(s.stepNo());
            }
        }
        return nos;
    }

    /** 422; top-level {@code CROSS_PLANT_REFERENCE} when any item is a cross-plant violation. */
    private static void throwIfAny(Errors errors) {
        if (errors.isEmpty()) {
            return;
        }
        List<FieldError> items = errors.items();
        boolean cross = items.stream().anyMatch(f -> ErrorCodes.CROSS_PLANT_REFERENCE.equals(f.code()));
        throw ApiException.unprocessable(cross ? ErrorCodes.CROSS_PLANT_REFERENCE : ErrorCodes.VALIDATION_FAILED,
                items.size() + " 个字段不合法", items);
    }

    private RoutingView view(Routing r) {
        List<RoutingStep> steps = stepMapper.selectList(
                new QueryWrapper<RoutingStep>().eq("routing_id", r.getId()).orderByAsc("step_no"));
        Map<UUID, Integer> noById = new HashMap<>();
        List<StepView> stepViews = new ArrayList<>();
        for (RoutingStep s : steps) {
            noById.put(s.getId(), s.getStepNo());
            stepViews.add(new StepView(s.getId(), s.getStepNo(), s.getOperationId(), s.getWorkCenterId(),
                    Boolean.TRUE.equals(s.getIsRework()), s.getStdTimeSec(), s.getRemark()));
        }
        List<EdgeView> edgeViews = edgeMapper.selectList(
                        new QueryWrapper<RoutingEdge>().eq("routing_id", r.getId()))
                .stream()
                .map(e -> new EdgeView(e.getId(), noById.get(e.getFromStepId()), noById.get(e.getToStepId()),
                        e.getEdgeType(), e.getDefectCodeId(), e.getRemark()))
                .sorted(Comparator.comparingInt(EdgeView::fromStepNo).thenComparingInt(EdgeView::toStepNo)
                        .thenComparing(EdgeView::edgeType))
                .toList();
        return new RoutingView(r.getId(), Scope.of(r.getPlantId()), r.getPlantId(), r.getCode(), r.getVersionNo(),
                r.getName(), r.getMaterialId(), r.getStatus(), r.getReleasedAt(), r.getRemark(), r.getLockVersion(),
                stepViews, edgeViews, r.getLayoutJson(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private Routing load(UUID id) {
        Routing r = mapper.selectById(id);
        if (r == null) {
            throw ApiException.notFound("工艺路线");
        }
        return r;
    }

}
