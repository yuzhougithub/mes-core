package com.xihua.mes.config.form;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.re2j.PatternSyntaxException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pure validation for custom-field schema documents (docs/architecture.md «动态表单»,
 * docs/v1-data-model.md «cfg_custom_field_schema»). No persistence.
 * <p>
 * The write path ({@link #validateWriteConfig(JsonNode)}) applies a strict key whitelist (A20):
 * unknown keys / {@code widget} / {@code x-*} → {@code SCHEMA_UNKNOWN_KEY} with a JSON Pointer.
 * User regexes are compiled with RE2J only (A17); {@code java.util.regex} is used solely for the
 * fixed, trusted key pattern below.
 */
public final class DynamicFormSchemaValidator {

    public static final int MAX_FIELDS = 50;
    public static final int MAX_PATTERN_CODE_POINTS = 200;
    public static final int MAX_TITLE_CODE_POINTS = 64;
    public static final int MAX_OPTIONS = 200;
    public static final int MAX_OPTION_VALUE_CODE_POINTS = 64;
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    /** Trusted constant pattern; not user input. */
    public static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");

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
        validateWriteConfigNode(MAPPER.valueToTree(schema));
    }

    /**
     * Strict write-config validation for raw JSON (whitelist + semantic rules).
     * Rejects Formily/widget and any other unknown keys with {@code SCHEMA_UNKNOWN_KEY}.
     */
    public static void validateWriteConfig(JsonNode root) {
        validateWriteConfigNode(root);
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

    /**
     * Rules that compare a new schema with the stored one (data model): an existing {@code key}
     * keeps its {@code type}. Renaming = remove + add, so it is not detectable and allowed.
     */
    public static void validateAgainstStored(JsonNode next, JsonNode stored) {
        if (stored == null || !stored.isObject() || !stored.path("fields").isArray()) {
            return;
        }
        java.util.Map<String, String> storedTypes = new java.util.HashMap<>();
        for (JsonNode f : stored.path("fields")) {
            if (f.path("key").isString() && f.path("type").isString()) {
                storedTypes.put(f.path("key").stringValue(), f.path("type").stringValue());
            }
        }
        JsonNode fields = next.path("fields");
        for (int i = 0; i < fields.size(); i++) {
            JsonNode f = fields.get(i);
            String key = f.path("key").stringValue();
            String before = storedTypes.get(key);
            if (before != null && !before.equals(f.path("type").stringValue())) {
                throw new DynamicFormSchemaException(
                        "SCHEMA_TYPE_IMMUTABLE",
                        "Field '" + key + "' type cannot change from " + before + " to "
                                + f.path("type").stringValue(),
                        "/fields/" + i + "/type");
            }
        }
    }

    private static void validateWriteConfigNode(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new DynamicFormSchemaException("Schema JSON must not be null");
        }
        if (!root.isObject()) {
            throw new DynamicFormSchemaException("Schema JSON must be an object");
        }
        rejectUnknownKeys(root, ROOT_KEYS, "");

        JsonNode versionNode = root.get("schemaVersion");
        int version = SUPPORTED_SCHEMA_VERSION;
        if (versionNode != null && !versionNode.isNull()) {
            if (!versionNode.isIntegralNumber()) {
                throw new DynamicFormSchemaException(
                        DynamicFormSchemaException.CODE_UNSUPPORTED_VERSION,
                        "schemaVersion must be an integer", "/schemaVersion");
            }
            version = versionNode.intValue();
        }
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_UNSUPPORTED_VERSION,
                    "Unsupported schemaVersion: " + version + " (only " + SUPPORTED_SCHEMA_VERSION + ")",
                    "/schemaVersion");
        }

        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || fieldsNode.isNull() || !fieldsNode.isArray()) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_INVALID, "fields must be an array", "/fields");
        }
        if (fieldsNode.size() > MAX_FIELDS) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_TOO_MANY_FIELDS,
                    "fields.length must be <= " + MAX_FIELDS + " but was " + fieldsNode.size(),
                    "/fields");
        }

        Set<String> keys = new HashSet<>();
        for (int i = 0; i < fieldsNode.size(); i++) {
            JsonNode field = fieldsNode.get(i);
            String path = "/fields/" + i;
            if (field == null || field.isNull() || !field.isObject()) {
                throw new DynamicFormSchemaException(
                        DynamicFormSchemaException.CODE_INVALID, "fields[" + i + "] must be an object", path);
            }
            rejectUnknownKeys(field, FIELD_KEYS, path);

            String key = textRequired(field.get("key"), path + "/key", "fields[" + i + "].key");
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        "fields[" + i + "].key must match ^[a-z][a-z0-9_]{0,39}$", path + "/key");
            }
            if (!keys.add(key)) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        "Duplicate field key: " + key, path + "/key");
            }
            JsonNode typeNode = field.get("type");
            if (typeNode == null || typeNode.isNull()) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID_TYPE,
                        "fields[" + i + "].type is required", path + "/type");
            }
            FieldType type;
            try {
                type = FieldType.from(typeNode.isString() ? typeNode.stringValue() : null);
            } catch (DynamicFormSchemaException ex) {
                throw new DynamicFormSchemaException(ex.getCode(), ex.getMessage(), path + "/type");
            }
            String title = textRequired(field.get("title"), path + "/title", "fields[" + i + "].title");
            if (title.codePointCount(0, title.length()) > MAX_TITLE_CODE_POINTS) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        "fields[" + i + "].title must be 1-" + MAX_TITLE_CODE_POINTS + " characters",
                        path + "/title");
            }

            JsonNode required = field.get("required");
            if (required != null && !required.isNull() && !required.isBoolean()) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        "fields[" + i + "].required must be a boolean", path + "/required");
            }

            JsonNode options = field.get("options");
            if (type.requiresOptions()) {
                if (options == null || options.isNull() || !options.isArray() || options.isEmpty()) {
                    throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                            "fields[" + i + "] type " + type.jsonName() + " requires a non-empty options array",
                            path + "/options");
                }
                validateOptionsNode(options, path + "/options", "fields[" + i + "].options");
            } else if (options != null && !options.isNull()) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        "fields[" + i + "] type " + type.jsonName() + " must not have options",
                        path + "/options");
            }

            JsonNode layout = field.get("layout");
            if (layout != null && !layout.isNull()) {
                validateLayoutNode(layout, path + "/layout", "fields[" + i + "].layout");
            }
            JsonNode validation = field.get("validation");
            if (validation != null && !validation.isNull()) {
                validateValidationNode(validation, type, path + "/validation", "fields[" + i + "].validation");
            }
        }
    }

    private static void validateOptionsNode(JsonNode options, String pointer, String label) {
        if (options.size() > MAX_OPTIONS) {
            throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                    label + " must have 1-" + MAX_OPTIONS + " items", pointer);
        }
        Set<String> values = new HashSet<>();
        for (int j = 0; j < options.size(); j++) {
            JsonNode opt = options.get(j);
            String optPointer = pointer + "/" + j;
            String optLabel = label + "[" + j + "]";
            if (opt == null || opt.isNull() || !opt.isObject()) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        optLabel + " must be an object", optPointer);
            }
            rejectUnknownKeys(opt, OPTION_KEYS, optPointer);
            String value = textRequired(opt.get("value"), optPointer + "/value", optLabel + ".value");
            if (value.codePointCount(0, value.length()) > MAX_OPTION_VALUE_CODE_POINTS) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        optLabel + ".value must be <= " + MAX_OPTION_VALUE_CODE_POINTS + " characters",
                        optPointer + "/value");
            }
            if (!values.add(value)) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        optLabel + ".value must be unique within the field", optPointer + "/value");
            }
            textRequired(opt.get("label"), optPointer + "/label", optLabel + ".label");
        }
    }

    private static void validateLayoutNode(JsonNode layout, String pointer, String label) {
        if (!layout.isObject()) {
            throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                    label + " must be an object", pointer);
        }
        rejectUnknownKeys(layout, LAYOUT_KEYS, pointer);
        JsonNode order = layout.get("order");
        if (order != null && !order.isNull() && !order.isNumber()) {
            throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                    label + ".order must be a number", pointer + "/order");
        }
        JsonNode group = layout.get("group");
        if (group != null && !group.isNull() && !group.isString()) {
            throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                    label + ".group must be a string", pointer + "/group");
        }
        JsonNode span = layout.get("span");
        if (span != null && !span.isNull()) {
            if (!span.isIntegralNumber() || span.intValue() < 1 || span.intValue() > 24) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        label + ".span must be an integer between 1 and 24", pointer + "/span");
            }
        }
    }

    private static Set<String> keywordsFor(FieldType type) {
        return switch (type) {
            case STRING -> Set.of("minLength", "maxLength", "pattern");
            case NUMBER -> Set.of("min", "max");
            case MULTI_ENUM -> Set.of("minItems", "maxItems");
            default -> Set.of();
        };
    }

    private static void validateValidationNode(JsonNode validation, FieldType type, String pointer, String label) {
        if (!validation.isObject()) {
            throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                    label + " must be an object", pointer);
        }
        rejectUnknownKeys(validation, VALIDATION_KEYS, pointer);
        Set<String> allowed = keywordsFor(type);
        for (String name : validation.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        label + "." + name + " is not applicable to type " + type.jsonName(),
                        pointer + "/" + name);
            }
        }
        for (String name : List.of("minLength", "maxLength", "minItems", "maxItems")) {
            JsonNode n = validation.get(name);
            if (n != null && !n.isNull() && (!n.isIntegralNumber() || n.longValue() < 0)) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        label + "." + name + " must be a non-negative integer", pointer + "/" + name);
            }
        }
        for (String name : List.of("min", "max")) {
            JsonNode n = validation.get(name);
            if (n != null && !n.isNull() && !n.isNumber()) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        label + "." + name + " must be a number", pointer + "/" + name);
            }
        }
        checkRange(validation, "minLength", "maxLength", pointer, label);
        checkRange(validation, "min", "max", pointer, label);
        checkRange(validation, "minItems", "maxItems", pointer, label);
        JsonNode pattern = validation.get("pattern");
        if (pattern != null && !pattern.isNull()) {
            if (!pattern.isString()) {
                throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                        label + ".pattern must be a string", pointer + "/pattern");
            }
            validatePattern(pattern.stringValue(), pointer + "/pattern", label + ".pattern");
        }
    }

    private static void checkRange(JsonNode v, String lo, String hi, String pointer, String label) {
        JsonNode a = v.get(lo);
        JsonNode b = v.get(hi);
        if (a != null && b != null && a.isNumber() && b.isNumber()
                && a.decimalValue().compareTo(b.decimalValue()) > 0) {
            throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                    label + "." + lo + " must be <= " + hi, pointer + "/" + lo);
        }
    }

    /** RE2J only (A17). */
    static void validatePattern(String pattern, String pointer, String label) {
        int codePoints = pattern.codePointCount(0, pattern.length());
        if (codePoints > MAX_PATTERN_CODE_POINTS) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_PATTERN_TOO_LONG,
                    label + " length must be <= " + MAX_PATTERN_CODE_POINTS + " code points but was " + codePoints,
                    pointer);
        }
        try {
            com.google.re2j.Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_PATTERN_INVALID,
                    label + " is not a valid RE2 pattern: " + ex.getDescription(),
                    pointer);
        }
    }

    private static void rejectUnknownKeys(JsonNode object, Set<String> allowed, String pointer) {
        Iterator<String> names = object.propertyNames().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw new DynamicFormSchemaException(
                        DynamicFormSchemaException.CODE_UNKNOWN_KEY,
                        "Unknown key '" + name + "' at " + (pointer.isEmpty() ? "/" : pointer),
                        pointer + "/" + escapePointer(name));
            }
        }
    }

    private static String escapePointer(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static String textRequired(JsonNode node, String pointer, String label) {
        if (node == null || node.isNull() || !node.isString() || node.stringValue().isBlank()) {
            throw new DynamicFormSchemaException(DynamicFormSchemaException.CODE_INVALID,
                    label + " must be a non-blank string", pointer);
        }
        return node.stringValue();
    }
}
