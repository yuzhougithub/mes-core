-- =====================================================================
-- V6：厂级 ADMIN 不再持有全局写权限点。
-- 这些点用在 @RequiresPerm(global = true)：工厂 / 菜单 / 角色定义的写，
-- 以及角色菜单授权（PUT /roles/{id}/menus 的 sys:role:grant）。
-- 读权限（list / view）和本厂用户、主数据维护仍留给 ADMIN。
-- 超级管理员不靠这个角色。已有迁移不改。
-- =====================================================================

DELETE FROM sys_role_menu AS rm
 USING org_role AS r, sys_menu AS m
 WHERE rm.role_id = r.id
   AND rm.menu_id = m.id
   AND r.code = 'ADMIN'
   AND NOT r.deleted
   AND m.perm_code IN (
        'org:plant:create',
        'org:plant:update',
        'org:plant:delete',
        'sys:menu:create',
        'sys:menu:update',
        'sys:menu:delete',
        'sys:role:create',
        'sys:role:update',
        'sys:role:delete',
        'sys:role:grant'
   );

-- 持有 ADMIN 的账号权限版本加一，避免沿用迁移前的权限快照。
UPDATE sys_user AS u
   SET perm_version = u.perm_version + 1,
       updated_at = now()
 WHERE NOT u.deleted
   AND EXISTS (
        SELECT 1
          FROM org_person_plant pp
          JOIN org_person_plant_role pr ON pr.person_plant_id = pp.id
          JOIN org_role r ON r.id = pr.role_id AND r.code = 'ADMIN' AND NOT r.deleted
         WHERE pp.person_id = u.person_id
   );
