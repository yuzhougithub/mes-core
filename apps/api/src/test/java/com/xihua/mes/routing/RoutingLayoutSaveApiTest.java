package com.xihua.mes.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xihua.mes.support.PgIntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * PR#6 test-review regressions (routing canvas layout save, docs/architecture.md «布局 layout»):
 * <ol>
 *   <li>Delete a step, PUT <em>without</em> {@code layout} → 200; orphan keys pruned in the same transaction.</li>
 *   <li>PUT with an explicit layout that still carries the orphan key → 422 {@code LAYOUT_INVALID}.</li>
 * </ol>
 */
class RoutingLayoutSaveApiTest extends PgIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String plant;
    private String op;

    @BeforeEach
    void setUp() throws Exception {
        plant = createPlant();
        op = body(mvc.perform(withPlant(post("/api/v1/operations"), plant).content("""
                        {"scope":"SHARED","code":"%s","name":"车削"}""".formatted(uniq("OP"))))
                .andExpect(status().isCreated()).andReturn()).get("id").stringValue();
    }

    private String steps(int... stepNos) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < stepNos.length; i++) {
            sb.append(i > 0 ? "," : "").append("{\"stepNo\":").append(stepNos[i])
                    .append(",\"operationId\":\"").append(op).append("\",\"isRework\":")
                    .append(stepNos[i] > 100).append('}');
        }
        return sb.append(']').toString();
    }

    private static final String LAYOUT_10_20_30 = """
            {"schemaVersion":1,"nodes":{"10":{"x":120,"y":80},"20":{"x":320,"y":80},"30":{"x":520,"y":80}}}""";

    private JsonNode createRouting() throws Exception {
        return body(mvc.perform(withPlant(post("/api/v1/routings"), plant).content("""
                        {"scope":"PLANT","code":"%s","versionNo":"A","name":"外壳加工","steps":%s,
                         "edges":[],"layout":%s}""".formatted(uniq("RT"), steps(10, 20, 30), LAYOUT_10_20_30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.layout.nodes.30.x").value(520))
                .andReturn());
    }

    private JsonNode storedLayout(String id) throws Exception {
        String raw = jdbc.queryForObject("SELECT layout_json::text FROM rtg_routing WHERE id = ?", String.class,
                UUID.fromString(id));
        return raw == null ? null : JSON.readTree(raw);
    }

    @Test
    void omittedLayoutAfterDeletingStepPrunesOrphanKeysAndReturns200() throws Exception {
        JsonNode r = createRouting();
        String id = r.get("id").stringValue();

        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"外壳加工","lockVersion":0,"steps":%s,"edges":[]}""".formatted(steps(10, 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.layout.nodes.10.x").value(120))
                .andExpect(jsonPath("$.layout.nodes.20.x").value(320))
                .andExpect(jsonPath("$.layout.nodes.30").doesNotExist())
                .andExpect(jsonPath("$.lockVersion").value(1));

        // persisted in the same save, not just filtered on read
        JsonNode stored = storedLayout(id);
        assertThat(stored.get("nodes").propertyNames()).containsExactlyInAnyOrder("10", "20");

        // and re-submitting the (now pruned) layout explicitly is accepted
        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"外壳加工","lockVersion":1,"steps":%s,"edges":[],"layout":%s}"""
                        .formatted(steps(10, 20), JSON.writeValueAsString(stored))))
                .andExpect(status().isOk());
    }

    @Test
    void explicitLayoutWithOrphanKeyIsRejected422AndNothingChanges() throws Exception {
        JsonNode r = createRouting();
        String id = r.get("id").stringValue();

        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"外壳加工","lockVersion":0,"steps":%s,"edges":[],"layout":%s}"""
                        .formatted(steps(10, 20), LAYOUT_10_20_30)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LAYOUT_INVALID"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("layout.nodes.30")));

        // rejected, not trimmed: steps and layout untouched, lock not bumped
        mvc.perform(get("/api/v1/routings/" + id).header("X-Plant-Id", plant))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.layout.nodes.30.x").value(520))
                .andExpect(jsonPath("$.lockVersion").value(0));
    }

    @Test
    void explicitNullClearsLayoutAndUnknownKeysAreRejected() throws Exception {
        String id = createRouting().get("id").stringValue();
        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"n","lockVersion":0,"steps":%s,"edges":[],
                         "layout":{"schemaVersion":1,"nodes":{"10":{"x":1,"y":2,"w":3}}}}""".formatted(steps(10))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LAYOUT_INVALID"));
        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"n","lockVersion":0,"steps":%s,"edges":[],"layout":null}""".formatted(steps(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layout").doesNotExist());
        org.junit.jupiter.api.Assertions.assertNull(storedLayout(id));
    }

    @Test
    void reworkEdgesUseStepNosAndNonDraftIsReadOnly() throws Exception {
        String id = createRouting().get("id").stringValue();
        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"n","lockVersion":0,"steps":%s,"edges":[
                          {"fromStepNo":20,"toStepNo":110,"edgeType":"REWORK"},
                          {"fromStepNo":110,"toStepNo":20,"edgeType":"RETURN"}]}""".formatted(steps(10, 20, 110))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(2))
                .andExpect(jsonPath("$.edges[0].fromStepNo").value(20))
                .andExpect(jsonPath("$.steps[2].isRework").value(true))
                .andExpect(jsonPath("$.layout.nodes.30").doesNotExist());

        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"n","lockVersion":1,"steps":%s,"edges":[
                          {"fromStepNo":20,"toStepNo":99,"edgeType":"SHORTCUT"}]}""".formatted(steps(10, 10))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].code", hasItem("DUPLICATE")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("edges[0].toStepNo")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("edges[0].edgeType")));

        jdbc.update("UPDATE rtg_routing SET status = 'RELEASED' WHERE id = ?", UUID.fromString(id));
        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"n","lockVersion":1,"steps":%s,"edges":[]}""".formatted(steps(10))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    void mainEdgesAreDroppedAndUnknownEdgeTypeIsRejected() throws Exception {
        String id = createRouting().get("id").stringValue();
        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"n","lockVersion":0,"steps":%s,"edges":[
                          {"fromStepNo":10,"toStepNo":20,"edgeType":"MAIN"},
                          {"fromStepNo":20,"toStepNo":30,"edgeType":"REWORK"}]}""".formatted(steps(10, 20, 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].edgeType").value("REWORK"))
                .andExpect(jsonPath("$.edges[0].fromStepNo").value(20))
                .andExpect(jsonPath("$.edges[0].toStepNo").value(30));

        assertThat(edgeTypes(id)).containsExactly("REWORK");

        mvc.perform(withPlant(put("/api/v1/routings/" + id), plant).content("""
                        {"name":"n","lockVersion":1,"steps":%s,"edges":[
                          {"fromStepNo":10,"toStepNo":20,"edgeType":"SHORTCUT"}]}""".formatted(steps(10, 20, 30))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("edges[0].edgeType")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("INVALID_VALUE")));

        assertThat(edgeTypes(id)).containsExactly("REWORK");
    }

    private List<String> edgeTypes(String routingId) {
        return jdbc.queryForList(
                "SELECT edge_type FROM rtg_routing_edge WHERE routing_id = ? ORDER BY edge_type",
                String.class, UUID.fromString(routingId));
    }

    @Test
    void sharedRoutingCannotReferencePlantPrivateData() throws Exception {
        String privateOp = body(mvc.perform(withPlant(post("/api/v1/operations"), plant).content("""
                        {"scope":"PLANT","code":"%s","name":"厂内工序"}""".formatted(uniq("OP"))))
                .andExpect(status().isCreated()).andReturn()).get("id").stringValue();
        mvc.perform(withPlant(post("/api/v1/routings"), plant).content("""
                        {"scope":"SHARED","code":"%s","versionNo":"A","name":"共享路线",
                         "steps":[{"stepNo":10,"operationId":"%s"}]}""".formatted(uniq("RT"), privateOp)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CROSS_PLANT_REFERENCE"))
                .andExpect(jsonPath("$.errors[0].field").value("steps[0].operationId"));

        // another plant cannot see the private operation at all
        String other = createPlant();
        mvc.perform(withPlant(post("/api/v1/routings"), other).content("""
                        {"scope":"PLANT","code":"%s","versionNo":"A","name":"他厂路线",
                         "steps":[{"stepNo":10,"operationId":"%s"}]}""".formatted(uniq("RT"), privateOp)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
    }

    @Test
    void sharedAndPlantVersionsCoexistButDuplicateVersionConflicts() throws Exception {
        String code = uniq("RT");
        String bodyTpl = """
                {"scope":"%s","code":"%s","versionNo":"A","name":"n","steps":[]}""";
        mvc.perform(withPlant(post("/api/v1/routings"), plant).content(bodyTpl.formatted("SHARED", code)))
                .andExpect(status().isCreated());
        mvc.perform(withPlant(post("/api/v1/routings"), plant).content(bodyTpl.formatted("PLANT", code)))
                .andExpect(status().isCreated());
        // (code, NULL, version) duplicate: NULLS NOT DISTINCT semantics
        mvc.perform(withPlant(post("/api/v1/routings"), plant).content(bodyTpl.formatted("SHARED", code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }
}
