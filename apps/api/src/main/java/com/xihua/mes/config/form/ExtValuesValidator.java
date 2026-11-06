package com.xihua.mes.config.form;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates and merges {@code ext_values} against the current custom-field schema
 * (docs/v1-data-model.md «扩展值 ext_values», A19/A21). Pure; file lookups go through {@link FileLookup}.
 * <ul>
 *   <li>Unknown key → {@code EXT_UNKNOWN_KEY}; never silently dropped.</li>
 *   <li>Whole replacement over configured keys: missing / null = cleared; required → {@code REQUIRED}.</li>
 *   <li>{@code boolean} required: {@code false} is a value (A21).</li>
 *   <li>Stored keys outside the schema (deleted fields) are kept as-is and never returned on read.</li>
 *   <li>On read, a stored value whose JSON shape does not match the current field type is kept
 *       but omitted (field deleted and recreated as a different type). Tighter rules — options,
 *       pattern, range, required — do not hide an already stored value (data-model «配置变更»).</li>
 *   <li>Strings ≤ 2000 chars checked <em>before</em> RE2J matching; serialized values ≤ 64 KB.</li>
 * </ul>
 */
public final class ExtValuesValidator {

    public static final int MAX_STRING_CODE_POINTS = 2000;
    public static final int MAX_SERIALIZED_BYTES = 64 * 1024;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Checks an {@code image}/{@code file} value (object key) exists in {@code cfg_file_object} and is visible. */
    @FunctionalInterface
    public interface FileLookup {
        /** @return content type of the visible file, or null when missing / not visible */
        String contentTypeOf(String objectKey);
    }

    public record Field(String key, FieldType type, String title, boolean required,
            Set<String> options, JsonNode validation) {
    }

    private ExtValuesValidator() {
    }

    /** Lenient read of a stored schema (read side may ignore unknown keys). */
    public static Map<String, Field> fieldsOf(JsonNode schema) {
        Map<String, Field> result = new LinkedHashMap<>();
        if (schema == null || !schema.path("fields").isArray()) {
            return result;
        }
        for (JsonNode f : schema.path("fields")) {
            String key = f.path("key").isString() ? f.path("key").stringValue() : null;
            FieldType type;
            try {
                type = FieldType.from(f.path("type").isString() ? f.path("type").stringValue() : null);
            } catch (DynamicFormSchemaException ex) {
                continue;
            }
            if (key == null) {
                continue;
            }
            Set<String> options = new HashSet<>();
            for (JsonNode o : f.path("options")) {
                if (o.path("value").isString()) {
                    options.add(o.path("value").stringValue());
                }
            }
            String title = f.path("title").isString() ? f.path("title").stringValue() : key;
            result.put(key, new Field(key, type, title, f.path("required").asBoolean(false), options,
                    f.get("validation")));
        }
        return result;
    }

    /**
     * Read projection: keys in the current schema whose stored JSON matches that field's type shape.
     * The stored document is not modified. Shape is the architecture «值的形状» (string, number,
     * boolean, offset date-time, string array, file key) — not options, pattern, range, or required.
     */
    public static ObjectNode project(JsonNode schema, JsonNode stored) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        if (stored == null || !stored.isObject()) {
            return out;
        }
        for (Field field : fieldsOf(schema).values()) {
            JsonNode v = stored.get(field.key());
            if (v != null && !v.isNull() && shapeMatches(field.type(), v)) {
                out.set(field.key(), v);
            }
        }
        return out;
    }

    /**
     * JSON shape for {@code type}. Does not apply {@code options}, {@code validation}, or required.
     */
    static boolean shapeMatches(FieldType type, JsonNode v) {
        return switch (type) {
            case STRING, ENUM, IMAGE, FILE -> v.isString();
            case NUMBER -> v.isNumber();
            case BOOLEAN -> v.isBoolean();
            case DATETIME -> v.isString() && isOffsetDateTime(v.stringValue());
            case MULTI_ENUM -> {
                if (!v.isArray()) {
                    yield false;
                }
                boolean ok = true;
                for (JsonNode item : v) {
                    if (!item.isString()) {
                        ok = false;
                        break;
                    }
                }
                yield ok;
            }
        };
    }

    private static boolean isOffsetDateTime(String value) {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    /**
     * Validate {@code requested} and merge into {@code stored}.
     *
     * @param fieldPrefix dot-path prefix for {@code errors[].field}, e.g. {@code values} / {@code extValues}
     * @return new stored document (configured keys replaced, others preserved)
     * @throws ApiException 422 {@code VALIDATION_FAILED}
     */
    public static ObjectNode validateAndMerge(JsonNode schema, JsonNode requested, JsonNode stored,
            String fieldPrefix, FileLookup files) {
        List<FieldError> errors = new ArrayList<>();
        Map<String, Field> fields = fieldsOf(schema);
        if (requested != null && !requested.isNull() && !requested.isObject()) {
            throw ApiException.validation(List.of(new FieldError(fieldPrefix, ErrorCodes.EXT_TYPE_MISMATCH,
                    fieldPrefix + " 必须是对象")));
        }
        JsonNode req = requested == null || requested.isNull() ? JsonNodeFactory.instance.objectNode() : requested;

        for (String name : req.propertyNames()) {
            if (!fields.containsKey(name)) {
                errors.add(new FieldError(fieldPrefix + "." + name, ErrorCodes.EXT_UNKNOWN_KEY,
                        "未配置的字段：" + name));
            }
        }

        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        if (stored != null && stored.isObject()) {
            for (String name : stored.propertyNames()) {
                if (!fields.containsKey(name)) {
                    merged.set(name, stored.get(name));
                }
            }
        }

        for (Field field : fields.values()) {
            String path = fieldPrefix + "." + field.key();
            JsonNode value = req.get(field.key());
            if (value == null || value.isNull()) {
                if (field.required()) {
                    errors.add(new FieldError(path, ErrorCodes.REQUIRED, field.title() + "必填"));
                }
                continue;
            }
            int before = errors.size();
            checkValue(field, value, path, errors, files);
            if (errors.size() == before) {
                merged.set(field.key(), value);
            }
        }

        if (errors.isEmpty()) {
            int bytes = MAPPER.writeValueAsString(merged).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_SERIALIZED_BYTES) {
                errors.add(new FieldError(fieldPrefix, ErrorCodes.TOO_LONG,
                        "扩展值序列化后不能超过 64 KB"));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return merged;
    }

    private static void checkValue(Field f, JsonNode v, String path, List<FieldError> errors, FileLookup files) {
        JsonNode rules = f.validation() == null ? JsonNodeFactory.instance.objectNode() : f.validation();
        switch (f.type()) {
            case STRING -> {
                if (!v.isString()) {
                    mismatch(f, path, errors);
                    return;
                }
                String s = v.stringValue();
                int len = s.codePointCount(0, s.length());
                if (len > MAX_STRING_CODE_POINTS) {
                    errors.add(new FieldError(path, ErrorCodes.TOO_LONG,
                            f.title() + "长度不能超过 " + MAX_STRING_CODE_POINTS));
                    return;
                }
                if (rules.path("maxLength").isNumber() && len > rules.path("maxLength").intValue()) {
                    errors.add(new FieldError(path, ErrorCodes.TOO_LONG,
                            f.title() + "长度不能超过 " + rules.path("maxLength").intValue()));
                    return;
                }
                if (rules.path("minLength").isNumber() && len < rules.path("minLength").intValue()) {
                    errors.add(new FieldError(path, ErrorCodes.OUT_OF_RANGE,
                            f.title() + "长度不能少于 " + rules.path("minLength").intValue()));
                    return;
                }
                if (rules.path("pattern").isString()) {
                    boolean ok;
                    try {
                        ok = com.google.re2j.Pattern.compile(rules.path("pattern").stringValue()).matcher(s).find();
                    } catch (RuntimeException ex) {
                        ok = false;
                    }
                    if (!ok) {
                        errors.add(new FieldError(path, ErrorCodes.PATTERN_MISMATCH, f.title() + "格式不符"));
                    }
                }
            }
            case NUMBER -> {
                if (!v.isNumber()) {
                    mismatch(f, path, errors);
                    return;
                }
                if (rules.path("min").isNumber() && v.decimalValue().compareTo(rules.path("min").decimalValue()) < 0
                        || rules.path("max").isNumber()
                                && v.decimalValue().compareTo(rules.path("max").decimalValue()) > 0) {
                    errors.add(new FieldError(path, ErrorCodes.OUT_OF_RANGE, f.title() + "超出范围"));
                }
            }
            case BOOLEAN -> {
                if (!v.isBoolean()) {
                    mismatch(f, path, errors);
                }
            }
            case DATETIME -> {
                if (!v.isString()) {
                    mismatch(f, path, errors);
                    return;
                }
                try {
                    OffsetDateTime.parse(v.stringValue());
                } catch (DateTimeParseException ex) {
                    errors.add(new FieldError(path, ErrorCodes.EXT_TYPE_MISMATCH,
                            f.title() + "须为带偏移的 ISO-8601 时间"));
                }
            }
            case ENUM -> {
                if (!v.isString()) {
                    mismatch(f, path, errors);
                    return;
                }
                if (!f.options().contains(v.stringValue())) {
                    errors.add(new FieldError(path, ErrorCodes.EXT_OPTION_INVALID, "不是可选值：" + v.stringValue()));
                }
            }
            case MULTI_ENUM -> {
                if (!v.isArray()) {
                    mismatch(f, path, errors);
                    return;
                }
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < v.size(); i++) {
                    JsonNode item = v.get(i);
                    if (!item.isString()) {
                        errors.add(new FieldError(path + "[" + i + "]", ErrorCodes.EXT_TYPE_MISMATCH,
                                f.title() + "元素须为字符串"));
                        return;
                    }
                    if (!f.options().contains(item.stringValue())) {
                        errors.add(new FieldError(path + "[" + i + "]", ErrorCodes.EXT_OPTION_INVALID,
                                "不是可选值：" + item.stringValue()));
                        return;
                    }
                    if (!seen.add(item.stringValue())) {
                        errors.add(new FieldError(path + "[" + i + "]", ErrorCodes.DUPLICATE,
                                "重复的选项：" + item.stringValue()));
                        return;
                    }
                }
                if (rules.path("minItems").isNumber() && v.size() < rules.path("minItems").intValue()
                        || rules.path("maxItems").isNumber() && v.size() > rules.path("maxItems").intValue()) {
                    errors.add(new FieldError(path, ErrorCodes.OUT_OF_RANGE, f.title() + "选择数量超出范围"));
                }
            }
            case IMAGE, FILE -> {
                if (!v.isString() || v.stringValue().isBlank()) {
                    mismatch(f, path, errors);
                    return;
                }
                String contentType = files == null ? null : files.contentTypeOf(v.stringValue());
                if (contentType == null) {
                    errors.add(new FieldError(path, ErrorCodes.FILE_NOT_FOUND, "文件不存在或不可见：" + v.stringValue()));
                } else if (f.type() == FieldType.IMAGE && !contentType.startsWith("image/")) {
                    errors.add(new FieldError(path, ErrorCodes.EXT_TYPE_MISMATCH, f.title() + "须为图片"));
                }
            }
            default -> mismatch(f, path, errors);
        }
    }

    private static void mismatch(Field f, String path, List<FieldError> errors) {
        errors.add(new FieldError(path, ErrorCodes.EXT_TYPE_MISMATCH,
                f.title() + "类型应为 " + f.type().jsonName()));
    }
}
