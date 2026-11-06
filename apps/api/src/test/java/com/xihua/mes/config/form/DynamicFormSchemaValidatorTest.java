package com.xihua.mes.config.form;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DynamicFormSchemaValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void acceptsValidSchema() {
        DynamicFormSchema schema = new DynamicFormSchema(
                1,
                List.of(
                        new FieldDefinition(
                                "surface_finish",
                                FieldType.ENUM,
                                "表面处理",
                                true,
                                List.of(new FieldOption("anodize", "阳极氧化")),
                                null,
                                new FieldLayout(10, "质量", 12)),
                        new FieldDefinition(
                                "notes",
                                FieldType.STRING,
                                "备注",
                                false,
                                null,
                                Map.of("pattern", "^[A-Za-z0-9_-]+$"),
                                null)));

        assertDoesNotThrow(() -> DynamicFormSchemaValidator.validate(schema));
    }

    @Test
    void rejectsDuplicateKey() {
        DynamicFormSchema schema = new DynamicFormSchema(
                1,
                List.of(
                        new FieldDefinition("a", FieldType.STRING, "A", null, null, null, null),
                        new FieldDefinition("a", FieldType.NUMBER, "B", null, null, null, null)));

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validate(schema));
        assertTrue(ex.getMessage().contains("Duplicate"));
    }

    @Test
    void rejectsEnumWithoutOptions() {
        DynamicFormSchema schema = new DynamicFormSchema(
                1,
                List.of(new FieldDefinition("color", FieldType.ENUM, "颜色", true, null, null, null)));

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validate(schema));
        assertTrue(ex.getMessage().contains("options"));
    }

    @Test
    void rejectsMultiEnumWithEmptyOptions() {
        DynamicFormSchema schema = new DynamicFormSchema(
                1,
                List.of(new FieldDefinition(
                        "tags", FieldType.MULTI_ENUM, "标签", false, List.of(), null, null)));

        assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validate(schema));
    }

    @Test
    void rejectsUnknownKeyInWriteConfig() throws Exception {
        JsonNode raw = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "x",
                      "type": "string",
                      "title": "X",
                      "widget": "Input"
                    }
                  ]
                }
                """);

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(raw));
        assertEquals(DynamicFormSchemaException.CODE_UNKNOWN_KEY, ex.getCode());
        assertTrue(ex.getMessage().contains("widget"));
    }

    @Test
    void rejectsForbiddenXComponentKey() throws Exception {
        JsonNode raw = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "x",
                      "type": "string",
                      "title": "X",
                      "x-component": "Input"
                    }
                  ]
                }
                """);

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateJson(raw));
        assertEquals(DynamicFormSchemaException.CODE_UNKNOWN_KEY, ex.getCode());
        assertTrue(ex.getMessage().contains("x-component"));
    }

    @Test
    void rejectsNestedUnknownLayoutKey() throws Exception {
        JsonNode raw = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "x",
                      "type": "string",
                      "title": "X",
                      "layout": { "x-reactions": [] }
                    }
                  ]
                }
                """);

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(raw));
        assertEquals(DynamicFormSchemaException.CODE_UNKNOWN_KEY, ex.getCode());
        assertTrue(ex.getMessage().contains("x-reactions"));
    }

    @Test
    void rejectsPatternTooLong() throws Exception {
        String pattern = "a".repeat(201);
        JsonNode raw = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "x",
                      "type": "string",
                      "title": "X",
                      "validation": { "pattern": "%s" }
                    }
                  ]
                }
                """.formatted(pattern));

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(raw));
        assertEquals(DynamicFormSchemaException.CODE_PATTERN_TOO_LONG, ex.getCode());
    }

    @Test
    void rejectsInvalidPattern() throws Exception {
        JsonNode raw = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "x",
                      "type": "string",
                      "title": "X",
                      "validation": { "pattern": "(?<=abc)def" }
                    }
                  ]
                }
                """);

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(raw));
        assertEquals(DynamicFormSchemaException.CODE_PATTERN_INVALID, ex.getCode());
    }

    @Test
    void rejectsSpanOutOfRange() throws Exception {
        JsonNode raw = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "x",
                      "type": "string",
                      "title": "X",
                      "layout": { "span": 25 }
                    }
                  ]
                }
                """);

        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(raw));
        assertTrue(ex.getMessage().contains("span"));
    }

    @Test
    void rejectsDuplicateOptionValueAndBlankLabel() throws Exception {
        JsonNode dup = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "color",
                      "type": "enum",
                      "title": "颜色",
                      "options": [
                        { "value": "r", "label": "红" },
                        { "value": "r", "label": "赤" }
                      ]
                    }
                  ]
                }
                """);
        DynamicFormSchemaException dupEx = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(dup));
        assertTrue(dupEx.getMessage().contains("unique"));

        JsonNode blankLabel = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "fields": [
                    {
                      "key": "color",
                      "type": "enum",
                      "title": "颜色",
                      "options": [ { "value": "r", "label": "  " } ]
                    }
                  ]
                }
                """);
        DynamicFormSchemaException labelEx = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(blankLabel));
        assertTrue(labelEx.getMessage().contains("label"));
    }

    @Test
    void rejectsTooManyFields() {
        List<FieldDefinition> fields = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            fields.add(new FieldDefinition("k" + i, FieldType.STRING, "T" + i, null, null, null, null));
        }
        DynamicFormSchema schema = new DynamicFormSchema(1, fields);
        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validate(schema));
        assertEquals(DynamicFormSchemaException.CODE_TOO_MANY_FIELDS, ex.getCode());
    }

    @Test
    void rejectsUnsupportedSchemaVersion() throws Exception {
        JsonNode raw = MAPPER.readTree("""
                {"schemaVersion": 2, "fields": [{"key":"x","type":"string","title":"X"}]}
                """);
        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> DynamicFormSchemaValidator.validateWriteConfig(raw));
        assertEquals(DynamicFormSchemaException.CODE_UNSUPPORTED_VERSION, ex.getCode());
    }

    @Test
    void defaultsMissingSchemaVersionToOne() throws Exception {
        JsonNode raw = MAPPER.readTree("""
                {"fields": [{"key":"x","type":"string","title":"X"}]}
                """);
        assertDoesNotThrow(() -> DynamicFormSchemaValidator.validateWriteConfig(raw));
    }

    @Test
    void unknownTypeIsSchemaInvalidTypeNotBareIae() {
        DynamicFormSchemaException ex = assertThrows(
                DynamicFormSchemaException.class,
                () -> FieldType.from("widget"));
        assertEquals(DynamicFormSchemaException.CODE_INVALID_TYPE, ex.getCode());
    }

    @Test
    void validateWriteConfigAcceptsMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("fields", List.of(Map.of(
                "key", "ok",
                "type", "boolean",
                "title", "OK")));
        assertDoesNotThrow(() -> DynamicFormSchemaValidator.validateWriteConfig(root));
    }

    @Test
    void validateTypedPlusJsonTogether() throws Exception {
        DynamicFormSchema schema = new DynamicFormSchema(
                1,
                List.of(new FieldDefinition("ok", FieldType.BOOLEAN, "OK", null, null, null, null)));
        JsonNode raw = MAPPER.readTree("""
                {"schemaVersion":1,"fields":[{"key":"ok","type":"boolean","title":"OK"}]}
                """);
        assertDoesNotThrow(() -> DynamicFormSchemaValidator.validate(schema, raw));
    }
}
