package com.xihua.mes.material.app;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.Errors;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.common.web.Scope;
import com.xihua.mes.config.app.ConfigQuery;
import com.xihua.mes.config.domain.CustomFieldEntityType;
import com.xihua.mes.material.api.MaterialDtos.MaterialCreate;
import com.xihua.mes.material.api.MaterialDtos.MaterialUpdate;
import com.xihua.mes.material.api.MaterialDtos.MaterialView;
import com.xihua.mes.material.api.MaterialDtos.PlantExtEmbedded;
import com.xihua.mes.material.api.MaterialDtos.PlantExtPut;
import com.xihua.mes.material.api.MaterialDtos.PlantExtView;
import com.xihua.mes.material.domain.Material;
import com.xihua.mes.material.domain.MaterialPlantExt;
import com.xihua.mes.material.domain.VersionStatus;
import com.xihua.mes.material.infra.MaterialMapper;
import com.xihua.mes.material.infra.MaterialPlantExtMapper;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materials: shared-type MDM ({@code plant_id} null = shared) + per-plant extension
 * ({@code mat_material_plant_ext}) carrying custom-field values. Visibility is enforced by the
 * MyBatis-Plus plant row filter: other plants' private rows behave as non-existent (404).
 */
@Service
public class MaterialService implements MaterialQuery {

    static final String DICT_MATERIAL_TYPE = "material_type";
    private static final Map<String, String> SORT = Map.of(
            "code", "code", "name", "name", "status", "status",
            "createdAt", "created_at", "updatedAt", "updated_at");

    private final MaterialMapper mapper;
    private final MaterialPlantExtMapper extMapper;
    private final UomService uoms;
    private final ConfigQuery config;

    public MaterialService(MaterialMapper mapper, MaterialPlantExtMapper extMapper, UomService uoms,
            ConfigQuery config) {
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.uoms = uoms;
        this.config = config;
    }

    public PageResult<MaterialView> list(PageQuery page, String q, String status, String materialType,
            UUID categoryId, String scope, List<UUID> ids) {
        QueryWrapper<Material> qw = new QueryWrapper<>();
        PageQuery.fuzzy(qw, q, "code", "name");
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        if (materialType != null && !materialType.isBlank()) {
            qw.eq("material_type", materialType);
        }
        if (categoryId != null) {
            qw.eq("category_id", categoryId);
        }
        if (scope != null && !scope.isBlank()) {
            if (Scope.parse(scope, "scope") == Scope.SHARED) {
                qw.isNull("plant_id");
            } else {
                qw.isNotNull("plant_id");
            }
        }
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > 200) {
                throw ApiException.validation(List.of(new com.xihua.mes.common.error.FieldError(
                        "ids", ErrorCodes.OUT_OF_RANGE, "ids 最多 200 个")));
            }
            qw.in("id", ids);
        }
        long total = mapper.selectCount(qw);
        page.applyOrderAndLimit(qw, SORT, "code");
        return new PageResult<>(mapper.selectList(qw).stream().map(m -> MaterialView.of(m, null)).toList(),
                page.page(), page.size(), total);
    }

    public MaterialView get(UUID id) {
        Material m = load(id);
        return MaterialView.of(m, embeddedExt(m.getId()));
    }

    @Override
    public Optional<MaterialRef> findVisible(UUID materialId) {
        if (materialId == null) {
            return Optional.empty();
        }
        Material m = mapper.selectById(materialId);
        return m == null ? Optional.empty() : Optional.of(new MaterialRef(m.getId(), m.getPlantId(), m.getStatus()));
    }

    @Transactional
    public MaterialView create(MaterialCreate req) {
        Scope scope = Scope.parseRequired(req.scope());
        Errors errors = new Errors();
        // V1 slice 1: no code-rule generation yet, so code is required.
        String code = errors.text("code", req.code(), true, 64);
        String name = errors.text("name", req.name(), true, 200);
        errors.text("spec", req.spec(), false, 500);
        errors.text("drawingNo", req.drawingNo(), false, 64);
        errors.text("remark", req.remark(), false, 500);
        validateRefs(errors, req.materialType(), req.uomId(), req.categoryId());
        errors.throwIfAny();
        if (mapper.codeTaken(code)) {
            throw ApiException.duplicateCode(code);
        }
        Material m = new Material();
        m.setId(UUID.randomUUID());
        m.setPlantId(scope.plantIdForCreate());
        m.setCode(code);
        m.setName(name);
        m.setSpec(req.spec());
        m.setMaterialType(req.materialType());
        m.setCategoryId(req.categoryId());
        m.setUomId(req.uomId());
        m.setDrawingNo(req.drawingNo());
        m.setRemark(req.remark());
        m.setStatus(VersionStatus.DRAFT.name());
        m.setLockVersion(0);
        m.setDeleted(false);
        mapper.insert(m);
        return get(m.getId());
    }

    @Transactional
    public MaterialView update(UUID id, MaterialUpdate req) {
        Material m = load(id);
        Locks.check(req.lockVersion(), m.getLockVersion());
        requireStatus(m, VersionStatus.DRAFT, "只有草稿物料可修改");
        Errors errors = new Errors();
        if (req.code() != null && !req.code().equals(m.getCode())) {
            errors.add("code", ErrorCodes.IMMUTABLE, "编码创建后不可改");
        }
        if (req.scope() != null && Scope.parse(req.scope(), "scope") != Scope.of(m.getPlantId())) {
            errors.add("scope", ErrorCodes.IMMUTABLE, "scope 创建后不可改");
        }
        String name = errors.text("name", req.name(), true, 200);
        errors.text("spec", req.spec(), false, 500);
        errors.text("drawingNo", req.drawingNo(), false, 64);
        errors.text("remark", req.remark(), false, 500);
        validateRefs(errors, req.materialType(), req.uomId(), req.categoryId());
        errors.throwIfAny();
        m.setName(name);
        m.setSpec(req.spec());
        m.setMaterialType(req.materialType());
        m.setCategoryId(req.categoryId());
        m.setUomId(req.uomId());
        m.setDrawingNo(req.drawingNo());
        m.setRemark(req.remark());
        Locks.requireUpdated(mapper.updateById(m));
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Integer lockVersion) {
        Material m = load(id);
        Locks.check(lockVersion, m.getLockVersion());
        requireStatus(m, VersionStatus.DRAFT, "只有草稿物料可删除");
        if (mapper.inUse(id)) {
            throw ApiException.inUse("物料已被引用");
        }
        Locks.requireUpdated(mapper.update(null, new UpdateWrapper<Material>()
                .set("deleted", true).eq("id", id).eq("lock_version", lockVersion)));
    }

    /** DRAFT → RELEASED; already RELEASED → 200 unchanged (A13); OBSOLETE → 409. */
    @Transactional
    public MaterialView release(UUID id, Integer lockVersion) {
        Material m = load(id);
        if (VersionStatus.RELEASED.name().equals(m.getStatus())) {
            return get(id);
        }
        Locks.check(lockVersion, m.getLockVersion());
        requireStatus(m, VersionStatus.DRAFT, "只有草稿可发布");
        m.setStatus(VersionStatus.RELEASED.name());
        m.setReleasedAt(OffsetDateTime.now());
        m.setReleasedBy(CurrentContext.userUuid());
        Locks.requireUpdated(mapper.updateById(m));
        return get(id);
    }

    /** RELEASED → OBSOLETE; already OBSOLETE → 200 unchanged; DRAFT → 409 (delete drafts instead). */
    @Transactional
    public MaterialView obsolete(UUID id, Integer lockVersion) {
        Material m = load(id);
        if (VersionStatus.OBSOLETE.name().equals(m.getStatus())) {
            return get(id);
        }
        Locks.check(lockVersion, m.getLockVersion());
        requireStatus(m, VersionStatus.RELEASED, "只有已发布可作废");
        m.setStatus(VersionStatus.OBSOLETE.name());
        Locks.requireUpdated(mapper.updateById(m));
        return get(id);
    }

    public PlantExtView getPlantExt(UUID materialId) {
        load(materialId);
        PlantExtEmbedded e = embeddedExt(materialId);
        return new PlantExtView(materialId, CurrentContext.requirePlantUuid(), e.enabled(), e.values(),
                e.lockVersion());
    }

    /**
     * Upsert the current plant's extension. First PUT sends {@code lockVersion = 0}; each save increments.
     * Values are validated + merged against the plant's {@code material} schema (A19/A21).
     * Writable while DRAFT or RELEASED; OBSOLETE → 409 STATE_CONFLICT.
     */
    @Transactional
    public PlantExtView putPlantExt(UUID materialId, PlantExtPut req) {
        Material m = load(materialId);
        if (VersionStatus.OBSOLETE.name().equals(m.getStatus())) {
            throw ApiException.stateConflict("物料已作废，扩展值只读");
        }
        MaterialPlantExt ext = findExt(materialId);
        Locks.check(req.lockVersion(), ext == null ? 0 : ext.getLockVersion());
        ObjectNode merged = config.validateExtValues(CustomFieldEntityType.MATERIAL, req.values(),
                ext == null ? null : ext.getExtValues(), "values");
        boolean enabled = req.enabled() == null || req.enabled();
        if (ext == null) {
            ext = new MaterialPlantExt();
            ext.setId(UUID.randomUUID());
            ext.setMaterialId(materialId);
            ext.setPlantId(CurrentContext.requirePlantUuid());
            ext.setEnabled(enabled);
            ext.setExtValues(merged);
            ext.setLockVersion(1);
            extMapper.insert(ext);
        } else {
            ext.setEnabled(enabled);
            ext.setExtValues(merged);
            Locks.requireUpdated(extMapper.updateById(ext));
        }
        return getPlantExt(materialId);
    }

    private PlantExtEmbedded embeddedExt(UUID materialId) {
        MaterialPlantExt ext = findExt(materialId);
        if (ext == null) {
            return new PlantExtEmbedded(true, JsonNodeFactory.instance.objectNode(), 0);
        }
        return new PlantExtEmbedded(Boolean.TRUE.equals(ext.getEnabled()),
                config.projectExtValues(CustomFieldEntityType.MATERIAL, ext.getExtValues()), ext.getLockVersion());
    }

    private MaterialPlantExt findExt(UUID materialId) {
        // plant_id = current appended by the row filter
        return extMapper.selectOne(new QueryWrapper<MaterialPlantExt>().eq("material_id", materialId));
    }

    private void validateRefs(Errors errors, String materialType, UUID uomId, UUID categoryId) {
        if (errors.text("materialType", materialType, true, 32) != null
                && !config.isDictValue(DICT_MATERIAL_TYPE, materialType)) {
            errors.add("materialType", ErrorCodes.DICT_VALUE_INVALID, "不是有效的物料类型：" + materialType);
        }
        if (errors.required("uomId", uomId) != null && !uoms.exists(uomId)) {
            errors.add("uomId", ErrorCodes.NOT_FOUND, "计量单位不存在");
        }
        if (categoryId != null && !mapper.categoryExists(categoryId)) {
            errors.add("categoryId", ErrorCodes.NOT_FOUND, "物料分类不存在");
        }
    }

    private static void requireStatus(Material m, VersionStatus expected, String message) {
        if (!Objects.equals(m.getStatus(), expected.name())) {
            throw ApiException.stateConflict(message + "（当前状态 " + m.getStatus() + "）");
        }
    }

    private Material load(UUID id) {
        Material m = mapper.selectById(id);
        if (m == null) {
            throw ApiException.notFound("物料");
        }
        return m;
    }

    public static List<UUID> parseIds(String raw) {
        List<UUID> ids = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            try {
                ids.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException ex) {
                throw ApiException.validation(List.of(new com.xihua.mes.common.error.FieldError(
                        "ids", ErrorCodes.INVALID_VALUE, "不是合法 UUID：" + part)));
            }
        }
        return ids;
    }
}
