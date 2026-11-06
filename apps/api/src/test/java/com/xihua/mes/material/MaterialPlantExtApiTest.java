package com.xihua.mes.material;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.xihua.mes.support.PgIntegrationTest;

/**
 * Shared MDM + plant extension + custom-field schema + plant row filter, end to end on PostgreSQL 17.
 */
class MaterialPlantExtApiTest extends PgIntegrationTest {

    private static final String SCHEMA = """
            {"lockVersion":%d,"schema":{"schemaVersion":1,"fields":[
              {"key":"surface_finish","type":"enum","title":"表面处理","required":true,
               "options":[{"value":"anodize","label":"阳极氧化"},{"value":"passivate","label":"钝化"}],
               "layout":{"order":10,"group":"质量","span":12}},
              {"key":"customer_pn","type":"string","title":"客户料号",
               "validation":{"maxLength":40,"pattern":"^[A-Z0-9-]+$"}},
              {"key":"rohs","type":"boolean","title":"RoHS","required":true}
            ]}}
            """;

    private String plantA;
    private String plantB;
    private String uom;

    @BeforeEach
    void setUp() throws Exception {
        plantA = createPlant();
        plantB = createPlant();
        uom = createUom(plantA);
    }

    private String createMaterial(String plant, String scope) throws Exception {
        String code = uniq("MAT");
        return body(mvc.perform(withPlant(post("/api/v1/materials"), plant).content("""
                        {"scope":"%s","code":"%s","name":"铝合金外壳","spec":"6061-T6","materialType":"SEMI",
                         "uomId":"%s","drawingNo":"DW-1001"}
                        """.formatted(scope, code, uom)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.scope").value(scope))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lockVersion").value(0))
                .andReturn()).get("id").stringValue();
    }

    @Test
    void businessEndpointsRequireAnExistingPlant() throws Exception {
        mvc.perform(get("/api/v1/materials"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PLANT_REQUIRED"));
        mvc.perform(get("/api/v1/materials").header("X-Plant-Id", "2f7a0c1e-0000-4000-8000-00000000dead"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLANT_FORBIDDEN"));
        mvc.perform(get("/api/v1/materials").header("X-Plant-Id", "not-a-uuid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLANT_FORBIDDEN"));
        // plants are global and need no plant context
        mvc.perform(get("/api/v1/plants")).andExpect(status().isOk());
    }

    @Test
    void sharedIsVisibleEverywherePrivateOnlyInItsPlant() throws Exception {
        String shared = createMaterial(plantA, "SHARED");
        String privateA = createMaterial(plantA, "PLANT");

        mvc.perform(get("/api/v1/materials/" + shared).header("X-Plant-Id", plantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantId").doesNotExist());
        mvc.perform(get("/api/v1/materials/" + privateA).header("X-Plant-Id", plantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantId").value(plantA));
        // other plant's private row: 404, not 403 (do not reveal existence)
        mvc.perform(get("/api/v1/materials/" + privateA).header("X-Plant-Id", plantB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // nor writable
        mvc.perform(withPlant(put("/api/v1/materials/" + privateA), plantB).content("""
                        {"name":"x","materialType":"SEMI","uomId":"%s","lockVersion":0}""".formatted(uom)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/materials").param("ids", shared + "," + privateA).header("X-Plant-Id", plantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(shared));
        mvc.perform(get("/api/v1/materials").param("ids", shared + "," + privateA)
                        .param("sort", "code,desc").header("X-Plant-Id", plantA))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/materials").param("ids", shared + "," + privateA)
                        .param("scope", "PLANT").header("X-Plant-Id", plantA))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(privateA));
    }

    @Test
    void createValidatesDictUomDuplicateAndSort() throws Exception {
        mvc.perform(withPlant(post("/api/v1/materials"), plantA).content("""
                        {"scope":"SHARED","code":"%s","name":"n","materialType":"NOPE",
                         "uomId":"2f7a0c1e-0000-4000-8000-0000000000aa"}""".formatted(uniq("M"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].code", hasItem("DICT_VALUE_INVALID")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("uomId")));
        String id = createMaterial(plantA, "SHARED");
        String code = body(mvc.perform(get("/api/v1/materials/" + id).header("X-Plant-Id", plantA)).andReturn())
                .get("code").stringValue();
        // codes are globally unique across plants (A5)
        mvc.perform(withPlant(post("/api/v1/materials"), plantB).content("""
                        {"scope":"PLANT","code":"%s","name":"n","materialType":"RAW","uomId":"%s"}"""
                        .formatted(code, uom)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        mvc.perform(get("/api/v1/materials").param("sort", "drop table,asc").header("X-Plant-Id", plantA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void customFieldSchemaWhitelistAndVersioning() throws Exception {
        mvc.perform(get("/api/v1/custom-field-schemas/material").header("X-Plant-Id", plantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema.fields").isEmpty())
                .andExpect(jsonPath("$.lockVersion").value(0));
        mvc.perform(withPlant(put("/api/v1/custom-field-schemas/material"), plantA).content("""
                        {"lockVersion":0,"schema":{"schemaVersion":1,"fields":[
                          {"key":"x","type":"string","title":"X","widget":"Input"}]}}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SCHEMA_UNKNOWN_KEY"))
                .andExpect(jsonPath("$.errors[0].field").value("/fields/0/widget"))
                .andExpect(jsonPath("$.traceId").isString());
        mvc.perform(withPlant(put("/api/v1/custom-field-schemas/material"), plantA).content("""
                        {"lockVersion":0,"schema":{"fields":[
                          {"key":"x","type":"string","title":"X","validation":{"pattern":"(?<=a)b"}}]}}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATTERN_INVALID"));
        mvc.perform(withPlant(put("/api/v1/custom-field-schemas/material"), plantA).content(SCHEMA.formatted(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockVersion").value(1))
                .andExpect(jsonPath("$.plantId").value(plantA))
                .andExpect(jsonPath("$.schema.fields.length()").value(3));
        mvc.perform(withPlant(put("/api/v1/custom-field-schemas/material"), plantA).content(SCHEMA.formatted(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        // type of an existing key is immutable
        mvc.perform(withPlant(put("/api/v1/custom-field-schemas/material"), plantA).content("""
                        {"lockVersion":1,"schema":{"schemaVersion":1,"fields":[
                          {"key":"rohs","type":"string","title":"RoHS"}]}}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("SCHEMA_TYPE_IMMUTABLE"));
        // per plant: plant B still has none
        mvc.perform(get("/api/v1/custom-field-schemas/material").header("X-Plant-Id", plantB))
                .andExpect(jsonPath("$.schema.fields").isEmpty());
        mvc.perform(get("/api/v1/custom-field-schemas/bom").header("X-Plant-Id", plantA))
                .andExpect(status().isNotFound());
    }

    @Test
    void plantExtValuesAreValidatedPerPlantAndSurviveRelease() throws Exception {
        mvc.perform(withPlant(put("/api/v1/custom-field-schemas/material"), plantA).content(SCHEMA.formatted(0)))
                .andExpect(status().isOk());
        String id = createMaterial(plantA, "SHARED");

        mvc.perform(get("/api/v1/materials/" + id).header("X-Plant-Id", plantA))
                .andExpect(jsonPath("$.plantExt.enabled").value(true))
                .andExpect(jsonPath("$.plantExt.values").isEmpty())
                .andExpect(jsonPath("$.plantExt.lockVersion").value(0));

        mvc.perform(withPlant(put("/api/v1/materials/" + id + "/plant-ext"), plantA).content("""
                        {"enabled":true,"lockVersion":0,"values":{"customer_pn":"bad pn","colour":"red"}}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].code", hasItem("REQUIRED")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("PATTERN_MISMATCH")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("EXT_UNKNOWN_KEY")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("values.colour")));

        // rohs=false satisfies required (A21)
        mvc.perform(withPlant(put("/api/v1/materials/" + id + "/plant-ext"), plantA).content("""
                        {"enabled":true,"lockVersion":0,"values":{"surface_finish":"anodize","customer_pn":"HX-1",
                         "rohs":false}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materialId").value(id))
                .andExpect(jsonPath("$.plantId").value(plantA))
                .andExpect(jsonPath("$.values.rohs").value(false))
                .andExpect(jsonPath("$.lockVersion").value(1));

        // plant B: no schema → any key unknown; its own ext row is independent
        mvc.perform(withPlant(put("/api/v1/materials/" + id + "/plant-ext"), plantB).content("""
                        {"enabled":false,"lockVersion":0,"values":{"surface_finish":"anodize"}}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("EXT_UNKNOWN_KEY"));
        mvc.perform(withPlant(put("/api/v1/materials/" + id + "/plant-ext"), plantB).content("""
                        {"enabled":false,"lockVersion":0,"values":{}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/v1/materials/" + id).header("X-Plant-Id", plantA))
                .andExpect(jsonPath("$.plantExt.enabled").value(true))
                .andExpect(jsonPath("$.plantExt.values.surface_finish").value("anodize"));

        // release: header read-only, ext still writable; stale lock → 409
        String lock = body(mvc.perform(get("/api/v1/materials/" + id).header("X-Plant-Id", plantA)).andReturn())
                .get("lockVersion").asString();
        mvc.perform(withPlant(post("/api/v1/materials/" + id + "/release"), plantA)
                        .content("{\"lockVersion\":" + lock + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releasedAt").isString());
        // idempotent release
        mvc.perform(withPlant(post("/api/v1/materials/" + id + "/release"), plantA).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        mvc.perform(withPlant(put("/api/v1/materials/" + id), plantA).content("""
                        {"name":"改名","materialType":"SEMI","uomId":"%s","lockVersion":1}""".formatted(uom)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mvc.perform(withPlant(put("/api/v1/materials/" + id + "/plant-ext"), plantA).content("""
                        {"enabled":true,"lockVersion":0,"values":{"surface_finish":"passivate","rohs":true}}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(withPlant(put("/api/v1/materials/" + id + "/plant-ext"), plantA).content("""
                        {"enabled":true,"lockVersion":1,"values":{"surface_finish":"passivate","rohs":true}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockVersion").value(2))
                .andExpect(jsonPath("$.values.customer_pn").doesNotExist());

        // obsolete: ext becomes read-only
        mvc.perform(withPlant(post("/api/v1/materials/" + id + "/obsolete"), plantA).content("{\"lockVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OBSOLETE"));
        mvc.perform(withPlant(put("/api/v1/materials/" + id + "/plant-ext"), plantA).content("""
                        {"enabled":true,"lockVersion":2,"values":{"surface_finish":"passivate","rohs":true}}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mvc.perform(get("/api/v1/materials/" + id + "/plant-ext").header("X-Plant-Id", plantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.surface_finish").value("passivate"));
    }

    @Test
    void draftUpdateAndDelete() throws Exception {
        String id = createMaterial(plantA, "PLANT");
        mvc.perform(withPlant(put("/api/v1/materials/" + id), plantA).content("""
                        {"name":"新名","materialType":"RAW","uomId":"%s","lockVersion":0,"scope":"SHARED"}"""
                        .formatted(uom)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("IMMUTABLE"));
        mvc.perform(withPlant(put("/api/v1/materials/" + id), plantA).content("""
                        {"name":"新名","materialType":"RAW","uomId":"%s","lockVersion":0}""".formatted(uom)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名"))
                .andExpect(jsonPath("$.spec").doesNotExist())
                .andExpect(jsonPath("$.lockVersion").value(1));
        mvc.perform(delete("/api/v1/materials/" + id).param("lockVersion", "0").header("X-Plant-Id", plantA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(delete("/api/v1/materials/" + id).param("lockVersion", "1").header("X-Plant-Id", plantA))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/materials/" + id).header("X-Plant-Id", plantA))
                .andExpect(status().isNotFound());
        // UoM in use by a (non-deleted) material cannot be deleted
        createMaterial(plantB, "PLANT");
        mvc.perform(delete("/api/v1/uoms/" + uom).param("lockVersion", "0").header("X-Plant-Id", plantA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"));
    }

    @Test
    void malformedJsonIsProblemBadRequest() throws Exception {
        mvc.perform(withPlant(post("/api/v1/materials"), plantA).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
