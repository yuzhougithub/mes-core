package com.xihua.mes.system.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.system.domain.SysMenu;

@Mapper
public interface MenuMapper extends BaseMapper<SysMenu> {

    @Select("""
            SELECT * FROM sys_menu
             WHERE NOT deleted
             ORDER BY sort_no, name, id
            """)
    List<SysMenu> listAll();

    @Select("""
            SELECT count(*) FROM sys_menu
             WHERE parent_id = #{id} AND NOT deleted
            """)
    int childCount(UUID id);

    @Select("""
            SELECT r.code
              FROM sys_role_menu rm
              JOIN org_role r ON r.id = rm.role_id AND NOT r.deleted
             WHERE rm.menu_id = #{id}
             ORDER BY r.code
            """)
    List<String> referencingRoleCodes(UUID id);

    @Select("""
            SELECT count(*) FROM sys_menu
             WHERE NOT deleted
               AND perm_code = #{permCode}
               AND id <> #{excludeId}
            """)
    int permCodeTaken(@Param("permCode") String permCode, @Param("excludeId") UUID excludeId);

    @Select("""
            SELECT count(*) FROM sys_menu
             WHERE NOT deleted
               AND type = 'MENU'
               AND path = #{path}
               AND id <> #{excludeId}
               AND parent_id IS NOT DISTINCT FROM #{parentId}
            """)
    int siblingPathTaken(
            @Param("path") String path,
            @Param("parentId") UUID parentId,
            @Param("excludeId") UUID excludeId);
}
