package com.xihua.mes.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xihua.mes.auth.PlantContext;
import com.xihua.mes.common.PlantRequiredException;
import com.xihua.mes.common.error.ApiException;

class PlantLineInnerInterceptorTest {

    private static final String P = "2f7a0c1e-0000-4000-8000-000000000001";
    private final PlantLineInnerInterceptor interceptor = new PlantLineInnerInterceptor();

    @BeforeEach
    void setUp() {
        PlantContext.set(P);
    }

    @AfterEach
    void tearDown() {
        PlantContext.clear();
    }

    private String rewrite(String sql) {
        return interceptor.parserSingle(sql, null);
    }

    @Test
    void sharedTableSeesSharedOrCurrentPlant() {
        String sql = rewrite("SELECT id, code FROM mat_material WHERE code = ? OR name = ?");
        assertThat(sql).contains("(plant_id IS NULL OR plant_id = '" + P + "')");
        // original OR must be parenthesized before AND-ing the plant predicate
        assertThat(sql).contains("(code = ? OR name = ?) AND");
    }

    @Test
    void plantOwnedAndExtensionTablesSeeOnlyCurrentPlant() {
        assertThat(rewrite("SELECT * FROM mat_material_plant_ext WHERE material_id = ?"))
                .contains("plant_id = '" + P + "'")
                .doesNotContain("IS NULL");
        assertThat(rewrite("SELECT * FROM cfg_custom_field_schema WHERE entity_type = ?"))
                .contains("plant_id = '" + P + "'");
    }

    @Test
    void globalAndChildTablesAreUntouched() {
        assertEquals("SELECT * FROM org_plant WHERE id = ?", rewrite("SELECT * FROM org_plant WHERE id = ?"));
        assertEquals("SELECT * FROM rtg_routing_step WHERE routing_id = ?",
                rewrite("SELECT * FROM rtg_routing_step WHERE routing_id = ?"));
    }

    @Test
    void joinsUseAliases() {
        String sql = rewrite("SELECT 1 FROM cfg_dict_item i JOIN cfg_dict_type t ON t.id = i.dict_type_id "
                + "WHERE t.code = ?");
        assertThat(sql).contains("i.plant_id IS NULL OR i.plant_id = '" + P + "'");
        assertThat(sql).doesNotContain("t.plant_id");
    }

    @Test
    void updateAndDeleteAreFiltered() {
        assertThat(rewrite("UPDATE mat_material SET name = ? WHERE id = ?"))
                .contains("plant_id IS NULL OR plant_id = '" + P + "'");
        assertThat(rewrite("DELETE FROM mat_material_plant_ext WHERE id = ?"))
                .contains("plant_id = '" + P + "'");
    }

    @Test
    void insertIsNeverRewritten() {
        String sql = "INSERT INTO mat_material (id, code) VALUES (?, ?)";
        assertEquals(sql, rewrite(sql));
    }

    @Test
    void failsClosedWithoutPlant() {
        PlantContext.clear();
        assertThrows(PlantRequiredException.class, () -> rewrite("SELECT * FROM mat_material"));
    }

    @Test
    void nonUuidPlantIsForbidden() {
        PlantContext.set("x' OR 1=1 --");
        ApiException ex = assertThrows(ApiException.class, () -> rewrite("SELECT * FROM mat_material"));
        assertEquals("PLANT_FORBIDDEN", ex.getCode());
    }

    @Test
    void scopeRegistryNormalizesNames() {
        assertEquals(PlantScope.SHARED, PlantScope.ofTable("\"public\".\"MAT_MATERIAL\""));
        assertEquals(PlantScope.PLANT, PlantScope.ofTable("res_equipment"));
        assertEquals(null, PlantScope.ofTable("org_plant"));
    }
}
