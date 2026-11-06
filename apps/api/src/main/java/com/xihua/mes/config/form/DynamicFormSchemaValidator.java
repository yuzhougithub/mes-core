package com.xihua.mes.config.form;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pure validation for dynamic-form metadata. No persistence.
 * <p>
 * Write-config path ({@link #validateWriteConfig(JsonNode)} / {@link #validateWriteConfig(Map)})
 * applies a strict key whitelist (PR#5 / A20). Typed DTO path remains available for in-process use.
 */
public final class DynamicFormSchemaValidator {

    public static final int MAX_FIELDS = 50;
    public static final int MAX_PATTERN_CODE_POINTS = 200;
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final Set<String> ROOT_KEYS = Set.of("schemaVersion", "fields");
    private static final Set<String> FIELD_KEYS = Set.of(
            "key", "type", "title", "required", "options", "validation", "layout");
    private static final Set<String> OPTION_KEYS = Set.of("value", "label");
    private static final Set<String> LAYOUT_KEYS = Set.of("order", "group", "span");
    private static final Set<String> VALIDATION_KEYS = Set.of(
            "minLength", "maxLength", "pattern", "min", "max", "minItems", "maxItems");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DynamicFormSchemaValidator() {
    }

    /** Validate a typed schema (key uniqueness, type rules, options, layout, pattern). */
    public static void validate(DynamicFormSchema schema) {
        if (schema == null) {
            throw new DynamicFormSchemaException("Schema must not be null");
        }
        int version = schema.schemaVersion() == null ? SUPPORTED_SCHEMA_VERSION : schema.schemaVersion();
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_UNSUPPORTED_VERSION,
                    "Unsupported schemaVersion: " + version + " (only " + SUPPORTED_SCHEMA_VERSION + ")");
        }
        List<FieldDefinition> fields = schema.fields();
        if (fields == null || fields.isEmpty()) {
            throw new DynamicFormSchemaException("fields must be a non-empty list");
        }
        if (fields.size() > MAX_FIELDS) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_TOO_MANY_FIELDS,
                    "fields.length must be <= " + MAX_FIELDS + " but was " + fields.size());
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
                throw new DynamicFormSchemaException(
                        DynamicFormSchemaException.CODE_INVALID_TYPE,
                        "fields[" + i + "].type is required");
            }
            if (field.title() == null || field.title().isBlank()) {
                throw new DynamicFormSchemaException("fields[" + i + "].title must be a non-blank string");
            }
            validateLayout(field.layout(), "fields[" + i + "].layout");
            validateValidationMap(field.validation(), "fields[" + i + "].validation");
            if (field.type().requiresOptions()) {
                validateOptions(field.options(), i);
            }
        }
    }

    /**
     * Strict write-config validation for raw JSON (whitelist + semantic rules).
     * Rejects Formily/widget and any other unknown keys with {@code SCHEMA_UNKNOWN_KEY}.
     */
    public static void validateWriteConfig(JsonNode root) {
        validateWriteConfigNode(root);
    }

    private static void validateWriteConfigNode(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new DynamicFormSchemaException("Schema JSON must not be null");
        }
        if (!root.isObject()) {
            throw new DynamicFormSchemaException("Schema JSON must be an object");
        }
        rejectUnknownKeys(root, ROOT_KEYS, "$");

        JsonNode versionNode = root.get("schemaVersion");
        int version = SUPPORTED_SCHEMA_VERSION;
        if (versionNode != null && !versionNode.isNull()) {
            if (!versionNode.isIntegralNumber()) {
                throw new DynamicFormSchemaException(
                        DynamicFormSchemaException.CODE_UNSUPPORTED_VERSION,
                        "schemaVersion must be an integer");
            }
            version = versionNode.intValue();
        }
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_UNSUPPORTED_VERSION,
                    "Unsupported schemaVersion: " + version + " (only " + SUPPORTED_SCHEMA_VERSION + ")");
        }

        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || fieldsNode.isNull() || !fieldsNode.isArray() || fieldsNode.isEmpty()) {
            throw new DynamicFormSchemaException("fields must be a non-empty array");
        }
        if (fieldsNode.size() > MAX_FIELDS) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_TOO_MANY_FIELDS,
                    "fields.length must be <= " + MAX_FIELDS + " but was " + fieldsNode.size());
        }

        Set<String> keys = new HashSet<>();
        for (int i = 0; i < fieldsNode.size(); i++) {
            JsonNode field = fieldsNode.get(i);
            String path = "$.fields[" + i + "]";
            if (field == null || field.isNull() || !field.isObject()) {
                throw new DynamicFormSchemaException(path + " must be an object");
            }
            rejectUnknownKeys(field, FIELD_KEYS, path);

            String key = textRequired(field.get("key"), path + ".key");
            if (!keys.add(key)) {
                throw new DynamicFormSchemaException("Duplicate field key: " + key);
            }
            FieldType type = FieldType.from(textRequired(field.get("type"), path + ".type"));
            textRequired(field.get("title"), path + ".title");

            if (field.has("required") && !field.get("required").isNull() && !field.get("required").isBoolean()) {
                throw new DynamicFormSchemaException(path + ".required must be a boolean");
            }

            JsonNode options = field.get("options");
            if (type.requiresOptions()) {
                if (options == null || options.isNull() || !options.isArray() || options.isEmpty()) {
                    throw new DynamicFormSchemaException(
                            path + " type " + type.jsonName() + " requires a non-empty options array");
                }
                validateOptionsNode(options, path + ".options");
            } else if (options != null && !options.isNull()) {
                if (!options.isArray()) {
                    throw new DynamicFormSchemaException(path + ".options must be an array");
                }
                validateOptionsNode(options, path + ".options");
            }

            if (field.has("layout") && !field.get("layout").isNull()) {
                validateLayoutNode(field.get("layout"), path + ".layout");
            }
            if (field.has("validation") && !field.get("validation").isNull()) {
                validateValidationNode(field.get("validation"), path + ".validation");
            }
        }
    }


    /** Strict write-config validation for a Map document (converted via Jackson). */
    public static void validateWriteConfig(Map<String, ?> root) {
        if (root == null) {
            throw new DynamicFormSchemaException("Schema must not be null");
        }
        validateWriteConfigNode(MAPPER.valueToTree(root));
    }

    /**
     * @deprecated Prefer {@link #validateWriteConfig(JsonNode)} for write APIs.
     * Kept as an alias so existing callers keep compiling.
     */
    @Deprecated
    public static void validateJson(JsonNode root) {
        validateWriteConfig(root);
    }

    /** Validate typed schema then optional raw JSON for write-config rules. */
    public static void validate(DynamicFormSchema schema, JsonNode raw) {
        validate(schema);
        if (raw != null) {
            validateWriteConfig(raw);
        }
    }

    private static void validateOptions(List<FieldOption> options, int fieldIndex) {
        if (options == null || options.isEmpty()) {
            throw new DynamicFormSchemaException(
                    "fields[" + fieldIndex + "] requires a non-empty options array");
        }
        Set<String> values = new HashSet<>();
        for (int j = 0; j < options.size(); j++) {
            FieldOption opt = options.get(j);
            String base = "fields[" + fieldIndex + "].options[" + j + "]";
            if (opt == null || opt.value() == null || opt.value().isBlank()) {
                throw new DynamicFormSchemaException(base + ".value must be a non-blank string");
            }
            if (!values.add(opt.value())) {
                throw new DynamicFormSchemaException(base + ".value must be unique within the field");
            }
            if (opt.label() == null || opt.label().isBlank()) {
                throw new DynamicFormSchemaException(base + ".label must be a non-blank string");
            }
        }
    }

    private static void validateOptionsNode(JsonNode options, String path) {
        Set<String> values = new HashSet<>();
        for (int j = 0; j < options.size(); j++) {
            JsonNode opt = options.get(j);
            String optPath = path + "[" + j + "]";
            if (opt == null || opt.isNull() || !opt.isObject()) {
                throw new DynamicFormSchemaException(optPath + " must be an object");
            }
            rejectUnknownKeys(opt, OPTION_KEYS, optPath);
            String value = textRequired(opt.get("value"), optPath + ".value");
            if (!values.add(value)) {
                throw new DynamicFormSchemaException(optPath + ".value must be unique within the field");
            }
            textRequired(opt.get("label"), optPath + ".label");
        }
    }

    private static void validateLayout(FieldLayout layout, String path) {
        if (layout == null) {
            return;
        }
        Integer span = layout.span();
        if (span != null && (span < 1 || span > 24)) {
            throw new DynamicFormSchemaException(path + ".span must be between 1 and 24");
        }
    }

    private static void validateLayoutNode(JsonNode layout, String path) {
        if (!layout.isObject()) {
            throw new DynamicFormSchemaException(path + " must be an object");
        }
        rejectUnknownKeys(layout, LAYOUT_KEYS, path);
        JsonNode span = layout.get("span");
        if (span != null && !span.isNull()) {
            if (!span.isIntegralNumber()) {
                throw new DynamicFormSchemaException(path + ".span must be an integer");
            }
            int value = span.intValue();
            if (value < 1 || value > 24) {
                throw new DynamicFormSchemaException(path + ".span must be between 1 and 24");
            }
        }
    }

    private static void validateValidationMap(Map<String, Object> validation, String path) {
        if (validation == null) {
            return;
        }
        for (String key : validation.keySet()) {
            if (!VALIDATION_KEYS.contains(key)) {
                throw new DynamicFormSchemaException(
                        DynamicFormSchemaException.CODE_UNKNOWN_KEY,
                        "Unknown key '" + key + "' at " + path);
            }
        }
        Object pattern = validation.get("pattern");
        if (pattern != null) {
            if (!(pattern instanceof String s)) {
                throw new DynamicFormSchemaException(path + ".pattern must be a string");
            }
            validatePattern(s, path + ".pattern");
        }
    }

    private static void validateValidationNode(JsonNode validation, String path) {
        if (!validation.isObject()) {
            throw new DynamicFormSchemaException(path + " must be an object");
        }
        rejectUnknownKeys(validation, VALIDATION_KEYS, path);
        JsonNode pattern = validation.get("pattern");
        if (pattern != null && !pattern.isNull()) {
            if (!pattern.isString()) {
                throw new DynamicFormSchemaException(path + ".pattern must be a string");
            }
            validatePattern(pattern.stringValue(), path + ".pattern");
        }
    }

    private static void validatePattern(String pattern, String path) {
        int codePoints = pattern.codePointCount(0, pattern.length());
        if (codePoints > MAX_PATTERN_CODE_POINTS) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_PATTERN_TOO_LONG,
                    path + " length must be <= " + MAX_PATTERN_CODE_POINTS
                            + " code points but was " + codePoints);
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_PATTERN_INVALID,
                    path + " is not a valid RE2 pattern: " + ex.getDescription());
        }
    }

    private static void rejectUnknownKeys(JsonNode object, Set<String> allowed, String path) {
        Iterator<String> names = object.propertyNames().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw new DynamicFormSchemaException(
                        DynamicFormSchemaException.CODE_UNKNOWN_KEY,
                        "Unknown key '" + name + "' at " + path);
            }
        }
    }

    private static String textRequired(JsonNode node, String path) {
        if (node == null || node.isNull() || !node.isString() || node.stringValue().isBlank()) {
            throw new DynamicFormSchemaException(path + " must be a non-blank string");
        }
        return node.stringValue();
    }
}
