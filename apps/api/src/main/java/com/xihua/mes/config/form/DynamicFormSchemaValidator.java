package com.xihua.mes.config.form;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * Pure validation for dynamic-form metadata. No persistence.
 * Rejects Formily / Designable exclusive keys anywhere in a JSON document.
 */
public final class DynamicFormSchemaValidator {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "widget",
            "x-component",
            "x-decorator",
            "x-designable-id",
            "x-reactions");

    private DynamicFormSchemaValidator() {
    }

    /** Validate a typed schema (key uniqueness, type rules, options). */
    public static void validate(DynamicFormSchema schema) {
        if (schema == null) {
            throw new DynamicFormSchemaException("Schema must not be null");
        }
        List<FieldDefinition> fields = schema.fields();
        if (fields == null || fields.isEmpty()) {
            throw new DynamicFormSchemaException("fields must be a non-empty list");
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);
            if (field == null) {
                throw new DynamicFormSchemaException("fields[" + i + "] must not be null");
            }
            if (field.key() == null || field.key().isBlank()) {
                throw new DynamicFormSchemaException("fields[" + i + "].key must be a non-blank string");
            }
            if (!keys.add(field.key())) {
                throw new DynamicFormSchemaException("Duplicate field key: " + field.key());
            }
            if (field.type() == null) {
                throw new DynamicFormSchemaException("fields[" + i + "].type is required");
            }
            if (field.title() == null || field.title().isBlank()) {
                throw new DynamicFormSchemaException("fields[" + i + "].title must be a non-blank string");
            }
            if (field.type().requiresOptions()) {
                List<FieldOption> options = field.options();
                if (options == null || options.isEmpty()) {
                    throw new DynamicFormSchemaException(
                            "fields[" + i + "] type " + field.type().jsonName()
                                    + " requires a non-empty options array");
                }
                for (int j = 0; j < options.size(); j++) {
                    FieldOption opt = options.get(j);
                    if (opt == null || opt.value() == null || opt.value().isBlank()) {
                        throw new DynamicFormSchemaException(
                                "fields[" + i + "].options[" + j + "].value must be a non-blank string");
                    }
                }
            }
        }
    }

    /**
     * Walk a raw JSON document and reject forbidden renderer-library keys
     * ({@code widget}, {@code x-component}, …) at any depth.
     */
    public static void validateJson(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new DynamicFormSchemaException("Schema JSON must not be null");
        }
        rejectForbiddenKeys(root, "$");
    }

    /** Validate typed schema then optional raw JSON for forbidden keys. */
    public static void validate(DynamicFormSchema schema, JsonNode raw) {
        validate(schema);
        if (raw != null) {
            validateJson(raw);
        }
    }

    private static void rejectForbiddenKeys(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String name = entry.getKey();
                if (FORBIDDEN_KEYS.contains(name)) {
                    throw new DynamicFormSchemaException(
                            "Forbidden key '" + name + "' at " + path
                                    + " (domain metadata must not use Formily/Designable keys)");
                }
                rejectForbiddenKeys(entry.getValue(), path + "." + name);
            }
        } else if (node.isArray()) {
            int i = 0;
            for (JsonNode child : node) {
                rejectForbiddenKeys(child, path + "[" + i + "]");
                i++;
            }
        }
    }
}
