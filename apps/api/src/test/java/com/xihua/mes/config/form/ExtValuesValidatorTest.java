package com.xihua.mes.config.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.FieldError;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ExtValuesValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final JsonNode SCHEMA = JSON.readTree("""
            {"schemaVersion":1,"fields":[
              {"key":"surface_finish","type":"enum","title":"表面处理","required":true,
               "options":[{"value":"anodize","label":"阳极氧化"},{"value":"passivate","label":"钝化"}]},
              {"key":"customer_pn","type":"string","title":"客户料号",
               "validation":{"maxLength":40,"pattern":"^[A-Z0-9-]+$"}},
              {"key":"rohs","type":"boolean","title":"RoHS","required":true},
              {"key":"weight","type":"number","title":"重量","validation":{"min":0,"max":100}},
              {"key":"tags","type":"multi_enum","title":"标签","options":[{"value":"a","label":"A"},
               {"value":"b","label":"B"}],"validation":{"maxItems":1}},
              {"key":"due","type":"datetime","title":"到期"},
              {"key":"photo","type":"image","title":"照片"},
              {"key":"drawing","type":"file","title":"图纸"}
            ]}
            """);

    private static final Map<String, String> FILES = Map.of(
            "plants/p/2026/11/img", "image/png",
            "plants/p/2026/11/pdf", "application/pdf");

    private static ObjectNode merge(String values, String stored) {
        return ExtValuesValidator.validateAndMerge(SCHEMA, JSON.readTree(values),
                stored == null ? null : JSON.readTree(stored), "values", FILES::get);
    }

    private static ApiException fail(String values) {
        return assertThrows(ApiException.class, () -> merge(values, null));
    }

    @Test
    void acceptsValidValuesAndBooleanFalseSatisfiesRequired() {
        ObjectNode out = merge("""
                {"surface_finish":"anodize","customer_pn":"HX-88021-A","rohs":false,"weight":12.5,
                 "tags":["a"],"due":"2026-11-06T17:30:00+08:00","photo":"plants/p/2026/11/img",
                 "drawing":"plants/p/2026/11/pdf"}
                """, null);
        assertEquals(false, out.get("rohs").booleanValue());
        assertEquals(8, out.size());
    }

    @Test
    void collectsAllErrorsWithSpecCodes() {
        ApiException ex = fail("""
                {"customer_pn":"lower","colour":"red","rohs":null,"weight":101,"tags":["a","b"],
                 "due":"yesterday","photo":"plants/p/2026/11/pdf","drawing":"missing"}
                """);
        assertEquals("VALIDATION_FAILED", ex.getCode());
        assertEquals(422, ex.getStatus().value());
        assertThat(ex.getErrors()).extracting(FieldError::field, FieldError::code).contains(
                org.assertj.core.groups.Tuple.tuple("values.colour", "EXT_UNKNOWN_KEY"),
                org.assertj.core.groups.Tuple.tuple("values.surface_finish", "REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("values.customer_pn", "PATTERN_MISMATCH"),
                org.assertj.core.groups.Tuple.tuple("values.rohs", "REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("values.weight", "OUT_OF_RANGE"),
                org.assertj.core.groups.Tuple.tuple("values.tags", "OUT_OF_RANGE"),
                org.assertj.core.groups.Tuple.tuple("values.due", "EXT_TYPE_MISMATCH"),
                org.assertj.core.groups.Tuple.tuple("values.photo", "EXT_TYPE_MISMATCH"),
                org.assertj.core.groups.Tuple.tuple("values.drawing", "FILE_NOT_FOUND"));
    }

    @Test
    void typeAndOptionChecks() {
        assertThat(fail("{\"surface_finish\":\"chrome\",\"rohs\":\"yes\"}").getErrors())
                .extracting(FieldError::code).containsExactlyInAnyOrder("EXT_OPTION_INVALID", "EXT_TYPE_MISMATCH");
        assertThat(fail("{\"surface_finish\":\"anodize\",\"rohs\":true,\"tags\":[\"z\"]}").getErrors())
                .extracting(FieldError::code).containsExactly("EXT_OPTION_INVALID");
    }

    @Test
    void lengthIsCheckedBeforePattern() {
        String longValue = "A".repeat(2001);
        ApiException ex = fail("{\"surface_finish\":\"anodize\",\"rohs\":true,\"customer_pn\":\"" + longValue + "\"}");
        assertThat(ex.getErrors()).extracting(FieldError::code).containsExactly("TOO_LONG");
    }

    @Test
    void wholeReplacementKeepsValuesOfRemovedFields() {
        ObjectNode out = merge("{\"surface_finish\":\"passivate\",\"rohs\":true}",
                "{\"surface_finish\":\"anodize\",\"customer_pn\":\"OLD\",\"legacy_field\":\"kept\"}");
        assertEquals("passivate", out.get("surface_finish").stringValue());
        assertEquals("kept", out.get("legacy_field").stringValue());
        // configured key not in request = cleared
        assertThat(out.has("customer_pn")).isFalse();
        // read projection hides keys outside the schema
        assertThat(ExtValuesValidator.project(SCHEMA, out).has("legacy_field")).isFalse();
    }

    @Test
    void valuesMustBeAnObject() {
        assertEquals("EXT_TYPE_MISMATCH", fail("[1]").getErrors().get(0).code());
    }

    @Test
    void projectOmitsValueWhenRecreatedFieldTypeDoesNotMatchShape() {
        JsonNode schema = JSON.readTree("""
                {"schemaVersion":1,"fields":[
                  {"key":"note","type":"number","title":"备注"},
                  {"key":"weight","type":"number","title":"重量"},
                  {"key":"rohs","type":"boolean","title":"RoHS"}
                ]}
                """);
        JsonNode stored = JSON.readTree("{\"note\":\"abc\",\"weight\":12.5,\"rohs\":false,\"legacy\":\"kept\"}");
        ObjectNode projected = ExtValuesValidator.project(schema, stored);
        assertThat(projected.has("note")).isFalse();
        assertEquals(12.5, projected.get("weight").doubleValue());
        assertEquals(false, projected.get("rohs").booleanValue());
        assertThat(projected.has("legacy")).isFalse();
        // stored row is left as-is
        assertEquals("abc", stored.get("note").stringValue());
    }

    @Test
    void projectKeepsSameShapeValuesThatFailTightenedRules() {
        JsonNode schema = JSON.readTree("""
                {"schemaVersion":1,"fields":[
                  {"key":"surface_finish","type":"enum","title":"表面处理",
                   "options":[{"value":"anodize","label":"阳极氧化"}]},
                  {"key":"customer_pn","type":"string","title":"客户料号","validation":{"maxLength":2}},
                  {"key":"due","type":"datetime","title":"到期"}
                ]}
                """);
        ObjectNode projected = ExtValuesValidator.project(schema, JSON.readTree("""
                {"surface_finish":"chrome","customer_pn":"HX-88021-A","due":"yesterday"}
                """));
        assertEquals("chrome", projected.get("surface_finish").stringValue());
        assertEquals("HX-88021-A", projected.get("customer_pn").stringValue());
        assertThat(projected.has("due")).isFalse();
    }
}
