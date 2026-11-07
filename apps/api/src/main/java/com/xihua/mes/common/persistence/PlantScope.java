package com.xihua.mes.common.persistence;

import java.util.Locale;
import java.util.Map;

/**
 * Row-visibility class of each table (docs/v1-master-data-overview.md «共享与厂级扩展»).
 * <ul>
 *   <li>{@link #SHARED}: {@code plant_id IS NULL OR plant_id = :current}</li>
 *   <li>{@link #PLANT}: {@code plant_id = :current} (plant-owned tables and {@code *_plant_ext})</li>
 *   <li>not listed: global or child table — no filter (children are reached through their root)</li>
 * </ul>
 */
public enum PlantScope {
    SHARED,
    PLANT;

    private static final Map<String, PlantScope> TABLES = Map.ofEntries(
            // shared MDM
            Map.entry("mat_material", SHARED),
            Map.entry("mat_bom", SHARED),
            Map.entry("rtg_operation", SHARED),
            Map.entry("rtg_routing", SHARED),
            Map.entry("cfg_dict_item", SHARED),
            Map.entry("cfg_defect_code", SHARED),
            Map.entry("cfg_reason_code", SHARED),
            Map.entry("cfg_code_rule", SHARED),
            Map.entry("cfg_file_object", SHARED),
            // plant-owned + plant extensions
            Map.entry("org_workshop", PLANT),
            Map.entry("org_work_center", PLANT),
            Map.entry("org_person_plant", PLANT),
            Map.entry("org_team", PLANT),
            Map.entry("res_equipment", PLANT),
            Map.entry("mat_material_plant_ext", PLANT),
            Map.entry("rtg_operation_plant_ext", PLANT),
            Map.entry("cfg_custom_field_schema", PLANT));

    /** @return scope for a (possibly quoted / schema-qualified) table name, or null if unfiltered */
    public static PlantScope ofTable(String tableName) {
        if (tableName == null) {
            return null;
        }
        String name = tableName.replace("\"", "");
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return TABLES.get(name.toLowerCase(Locale.ROOT));
    }
}
