package com.xihua.mes.config.form;

import java.util.List;

/**
 * Dynamic form field metadata document: {@code schemaVersion} + {@code fields}.
 * Persistence and HTTP API await the master-data / custom-field spec.
 */
public record DynamicFormSchema(Integer schemaVersion, List<FieldDefinition> fields) {
}
