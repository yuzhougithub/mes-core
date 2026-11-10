package com.xihua.mes.auth.infra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PermissionMapper {

    @Select("""
            SELECT super_admin, perm_version
              FROM sys_user
             WHERE id = #{id} AND NOT deleted
            """)
    UserPermState userState(UUID id);

    @Select("""
            SELECT DISTINCT m.perm_code
              FROM org_person_plant pp
              JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
              JOIN org_role r ON r.id = pr.role_id AND NOT r.deleted AND r.enabled
              JOIN sys_role_menu rm ON rm.role_id = r.id
              JOIN sys_menu m ON m.id = rm.menu_id AND NOT m.deleted AND m.enabled
             WHERE pp.person_id = #{personId}
               AND pp.plant_id = #{plantId}
               AND pp.active
               AND m.perm_code IS NOT NULL
            """)
    List<String> permCodes(@Param("personId") UUID personId, @Param("plantId") UUID plantId);

    /** Permission points the caller's roles include, whether or not an ancestor is enabled. */
    @Select("""
            SELECT DISTINCT m.perm_code
              FROM org_person_plant pp
              JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
              JOIN org_role r ON r.id = pr.role_id AND NOT r.deleted AND r.enabled
              JOIN sys_role_menu rm ON rm.role_id = r.id
              JOIN sys_menu m ON m.id = rm.menu_id AND NOT m.deleted AND m.perm_code IS NOT NULL
             WHERE pp.person_id = #{personId}
               AND pp.plant_id = #{plantId}
               AND pp.active
            """)
    List<String> heldPermCodes(@Param("personId") UUID personId, @Param("plantId") UUID plantId);

    @Select("""
            SELECT DISTINCT m.perm_code
              FROM sys_role_menu rm
              JOIN sys_menu m ON m.id = rm.menu_id AND NOT m.deleted AND m.perm_code IS NOT NULL
             WHERE rm.role_id = #{roleId}
             ORDER BY m.perm_code
            """)
    List<String> rolePermCodes(UUID roleId);

    @Select("""
            SELECT id, parent_id, type, name, perm_code, path, component, icon, sort_no, visible, enabled
              FROM sys_menu
             WHERE NOT deleted
             ORDER BY sort_no, name, id
            """)
    List<MenuRow> menus();

    @Select("""
            SELECT perm_code FROM sys_menu
             WHERE perm_code IS NOT NULL AND NOT deleted
            """)
    List<String> storedPermCodes();

    @Update("""
            UPDATE sys_user u
               SET perm_version = u.perm_version + 1, updated_at = #{now}
             WHERE NOT u.deleted
               AND EXISTS (
                    SELECT 1
                      FROM org_person_plant pp
                      JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
                     WHERE pp.person_id = u.person_id
                       AND pr.role_id = #{roleId}
               )
            """)
    int bumpRoleHolders(@Param("roleId") UUID roleId, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE sys_user u
               SET perm_version = u.perm_version + 1, updated_at = #{now}
             WHERE NOT u.deleted
               AND EXISTS (
                    WITH RECURSIVE down AS (
                        SELECT id FROM sys_menu WHERE id = #{menuId}
                        UNION ALL
                        SELECT child.id
                          FROM sys_menu child
                          JOIN down ON child.parent_id = down.id
                    )
                    SELECT 1
                      FROM org_person_plant pp
                      JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
                      JOIN sys_role_menu rm ON rm.role_id = pr.role_id
                      JOIN down ON down.id = rm.menu_id
                     WHERE pp.person_id = u.person_id
               )
            """)
    int bumpMenuHolders(@Param("menuId") UUID menuId, @Param("now") OffsetDateTime now);
}
