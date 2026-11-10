package com.xihua.mes.system.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMenuMapper {

    @Select("""
            SELECT menu_id FROM sys_role_menu
             WHERE role_id = #{roleId}
             ORDER BY menu_id
            """)
    List<UUID> menuIds(UUID roleId);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRole(UUID roleId);

    @Insert("""
            INSERT INTO sys_role_menu (role_id, menu_id, created_by)
            VALUES (#{roleId}, #{menuId}, #{createdBy})
            """)
    int insert(@Param("roleId") UUID roleId, @Param("menuId") UUID menuId, @Param("createdBy") UUID createdBy);
}
