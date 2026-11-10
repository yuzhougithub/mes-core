package com.xihua.mes.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.xihua.mes.auth.PlantContext;

/** SQL shape of the six data scopes, and that the plant filter still applies beside them. */
class DataScopeInnerInterceptorTest {

    private static final String PLANT = "2f7a0c1e-0000-4000-8000-000000000001";
    private static final UUID DEPT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID OTHER_DEPT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000002");
    private static final UUID USER = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000001");
    private static final String ANCESTORS = "/" + DEPT + "/";

    private final DataScopeInnerInterceptor scope = new DataScopeInnerInterceptor();
    private final PlantLineInnerInterceptor plant = new PlantLineInnerInterceptor();

    @AfterEach
    void tearDown() {
        DataScopeHolder.clear();
        DataScopeBridge.clear();
        PlantContext.clear();
    }

    @Test
    void sixScopesOnPerson() {
        DataScopeHolder.push(null, true, false);
        assertThat(rewrite(scopeOf("DEPT"))).contains("dept_id = '" + DEPT + "'");
        assertThat(rewrite(scopeOf("DEPT_AND_CHILD")))
                .contains("ancestors LIKE '" + ANCESTORS + "%'")
                .contains("org_dept");
        assertThat(rewrite(scopeOf("SELF"))).contains("created_by = '" + USER + "'");
        assertThat(rewrite(scopeOf("CUSTOM"))).contains("dept_id IN ('" + OTHER_DEPT + "')");
        assertEquals("SELECT id FROM org_person", rewrite(wide("ALL", true)));
        assertEquals("SELECT id FROM org_person", rewrite(wide("PLANT", false)));
    }

    @Test
    void accountScopeUsesPersonDeptAndOwnUserId() {
        DataScopeHolder.push(null, true, false);
        String sql = rewrite("SELECT id FROM sys_user", decision(false, false, Set.of("DEPT", "SELF"),
                List.of()));
        assertThat(sql).contains("person_id IN (SELECT id FROM org_person WHERE NOT deleted AND dept_id = '"
                + DEPT + "')");
        assertThat(sql).contains("id = '" + USER + "'");
        assertThat(sql).doesNotContain("created_by");
        assertThat(sql).containsIgnoringCase(" or ");
    }

    @Test
    void operLogUsesUserIdAndIgnoresDepartmentScopes() {
        DataScopeHolder.push(null, true, false);
        String sql = rewrite("SELECT id FROM sys_oper_log", decision(false, false, Set.of("DEPT", "SELF"),
                List.of()));
        assertThat(sql).contains("user_id = '" + USER + "'");
        assertThat(sql).contains("1 = 0");
    }

    @Test
    void noRolesMatchNothing() {
        DataScopeHolder.push(null, true, false);
        assertThat(rewrite(decision(false, false, Set.of(), List.of()))).contains("1 = 0");
    }

    @Test
    void unmarkedStatementsAreNotScoped() {
        DataScopeBridge.override(scopeOf("DEPT"));
        assertEquals("SELECT id FROM org_person", scope.parserSingle("SELECT id FROM org_person", "x.Y.list"));
    }

    @Test
    void aliasLimitsThePredicate() {
        DataScopeHolder.push("p", true, false);
        String sql = rewrite("SELECT p.id FROM org_person p JOIN org_person o ON o.id = p.id", scopeOf("DEPT"));
        assertThat(sql).contains("p.dept_id = '" + DEPT + "'");
        assertThat(sql).doesNotContain("o.dept_id");
    }

    @Test
    void plantFilterStillAppliesToTheScopeSubquery() {
        PlantContext.set(PLANT);
        DataScopeHolder.push(null, true, false);
        String scoped = rewrite(scopeOf("DEPT_AND_CHILD"));
        String both = plant.parserSingle(scoped, "x.Y.list");
        assertThat(both).contains("ancestors LIKE '" + ANCESTORS + "%'");
        assertThat(both).contains("plant_id IS NULL OR");
        assertThat(both).contains("plant_id = '" + PLANT + "'");
    }

    @Test
    void crossPlantAllUsesAssignedPlantsAndSuperAdminSkipsThePlantPredicate() {
        PlantContext.set(PLANT);
        UUID second = UUID.fromString("cccccccc-0000-4000-8000-000000000002");
        DataScopeHolder.push(null, false, true);
        DataScopeBridge.override(new DataScopeDecision(false, true, Set.of("ALL"), null, null, USER,
                List.of(), List.of(UUID.fromString(PLANT), second)));
        String all = plant.parserSingle("SELECT id FROM org_workshop", "x.Y.tree");
        assertThat(all).contains("plant_id IN");
        assertThat(all).contains(second.toString());

        DataScopeBridge.override(new DataScopeDecision(true, false, Set.of(), null, null, USER, List.of(), List.of()));
        assertEquals("SELECT id FROM org_workshop", plant.parserSingle("SELECT id FROM org_workshop", "x.Y.tree"));

        DataScopeBridge.override(new DataScopeDecision(false, false, Set.of("PLANT"), null, null, USER,
                List.of(), List.of(UUID.fromString(PLANT), second)));
        String plantOnly = plant.parserSingle("SELECT id FROM org_workshop", "x.Y.tree");
        assertThat(plantOnly).contains("plant_id = '" + PLANT + "'");
        assertThat(plantOnly).doesNotContain(second.toString());
    }

    private String rewrite(DataScopeDecision decision) {
        return rewrite("SELECT id FROM org_person", decision);
    }

    private String rewrite(String sql, DataScopeDecision decision) {
        DataScopeBridge.override(decision);
        return scope.parserSingle(sql, "x.Y.list");
    }

    private static DataScopeDecision scopeOf(String scope) {
        List<UUID> custom = "CUSTOM".equals(scope) ? List.of(OTHER_DEPT) : List.of();
        return decision(false, false, Set.of(scope), custom);
    }

    private static DataScopeDecision wide(String scope, boolean all) {
        return decision(false, all, Set.of(scope), List.of());
    }

    private static DataScopeDecision decision(boolean unrestricted, boolean all, Set<String> scopes, List<UUID> custom) {
        return new DataScopeDecision(unrestricted, all, scopes, DEPT, ANCESTORS, USER, custom, List.of());
    }
}
