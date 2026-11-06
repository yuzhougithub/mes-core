package com.xihua.mes.config.form;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
                                null,
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
    void rejectsForbiddenWidgetKeyInJson() throws Exception {
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
                () -> DynamicFormSchemaValidator.validateJson(raw));
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
        assertTrue(ex.getMessage().contains("x-component"));
    }

    @Test
    void rejectsNestedForbiddenKeys() throws Exception {
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
                () -> DynamicFormSchemaValidator.validateJson(raw));
        assertTrue(ex.getMessage().contains("x-reactions"));
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
