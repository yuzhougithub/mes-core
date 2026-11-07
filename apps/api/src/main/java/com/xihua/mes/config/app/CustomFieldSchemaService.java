package com.xihua.mes.config.app;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;
import com.xihua.mes.common.web.Locks;
import com.xihua.mes.config.api.CustomFieldSchemaDtos.SchemaView;
import com.xihua.mes.config.domain.CustomFieldEntityType;
import com.xihua.mes.config.domain.CustomFieldSchema;
import com.xihua.mes.config.form.DynamicFormSchemaValidator;
import com.xihua.mes.config.form.ExtValuesValidator;
import com.xihua.mes.config.infra.ConfigLookupMapper;
import com.xihua.mes.config.infra.CustomFieldSchemaMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Custom-field schema per (current plant, entity type) + ext-value validation entry point. */
@Service
public class CustomFieldSchemaService implements ConfigQuery {

    private final CustomFieldSchemaMapper mapper;
    private final ConfigLookupMapper lookups;

    public CustomFieldSchemaService(CustomFieldSchemaMapper mapper, ConfigLookupMapper lookups) {
        this.mapper = mapper;
        this.lookups = lookups;
    }

    public static CustomFieldEntityType parseEntityType(String raw) {
        return CustomFieldEntityType.fromWire(raw).orElseThrow(() -> ApiException.notFound("实体类型 " + raw));
    }

    public static ObjectNode emptySchema() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("schemaVersion", 1);
        n.putArray("fields");
        return n;
    }

    public SchemaView get(CustomFieldEntityType type) {
        CustomFieldSchema row = find(type);
        UUID plant = CurrentContext.requirePlantUuid();
        if (row == null) {
            return new SchemaView(type.wire(), plant, emptySchema(), 0, null);
        }
        return SchemaView.of(row);
    }

    /**
     * PUT {@code { schema, lockVersion }}. First save uses {@code lockVersion = 0}; every successful save
     * increments (first save returns 1, per docs/v1-api.md example 1).
     */
    @Transactional
    public SchemaView put(CustomFieldEntityType type, JsonNode schema, Integer lockVersion) {
        if (schema == null || schema.isNull() || schema.isMissingNode()) {
            throw ApiException.validation(List.of(new FieldError("schema", ErrorCodes.REQUIRED, "schema 必填")));
        }
        DynamicFormSchemaValidator.validateWriteConfig(schema);
        ObjectNode normalized = (ObjectNode) schema.deepCopy();
        if (!normalized.has("schemaVersion") || normalized.get("schemaVersion").isNull()) {
            normalized.put("schemaVersion", 1);
        }
        CustomFieldSchema row = find(type);
        if (row == null) {
            Locks.check(lockVersion, 0);
            CustomFieldSchema created = new CustomFieldSchema();
            created.setId(UUID.randomUUID());
            created.setPlantId(CurrentContext.requirePlantUuid());
            created.setEntityType(type.wire());
            created.setSchema(normalized);
            created.setLockVersion(1);
            mapper.insert(created);
        } else {
            Locks.check(lockVersion, row.getLockVersion());
            DynamicFormSchemaValidator.validateAgainstStored(normalized, row.getSchema());
            row.setSchema(normalized);
            Locks.requireUpdated(mapper.updateById(row));
        }
        return SchemaView.of(find(type));
    }

    @Override
    public boolean isDictValue(String typeCode, String value) {
        return value != null && lookups.dictValueExists(typeCode, value);
    }

    @Override
    public java.util.Optional<com.xihua.mes.common.web.ScopedRef> findDefectCode(UUID id) {
        return id == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(lookups.defectCode(id));
    }

    @Override
    public JsonNode currentSchema(CustomFieldEntityType entityType) {
        CustomFieldSchema row = find(entityType);
        return row == null ? emptySchema() : row.getSchema();
    }

    @Override
    public ObjectNode validateExtValues(CustomFieldEntityType entityType, JsonNode requested, JsonNode stored,
            String fieldPrefix) {
        return ExtValuesValidator.validateAndMerge(currentSchema(entityType), requested, stored, fieldPrefix,
                lookups::fileContentType);
    }

    @Override
    public ObjectNode projectExtValues(CustomFieldEntityType entityType, JsonNode stored) {
        return ExtValuesValidator.project(currentSchema(entityType), stored);
    }

    private CustomFieldSchema find(CustomFieldEntityType type) {
        // plant_id = current is appended by the plant row filter
        return mapper.selectOne(new QueryWrapper<CustomFieldSchema>().eq("entity_type", type.wire()));
    }
}
