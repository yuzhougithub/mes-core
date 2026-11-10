-- =====================================================================
-- V4：F2 菜单与角色（docs/v1-foundation.md §3.3 / §4.2 / §5）。
-- 种子只在本迁移插入一次，之后可在页面改，启动时不再重放。
-- 角色仍按工厂授予（已有 org_person_plant_role）。
-- 需要 PostgreSQL >= 15；目标 PostgreSQL 17。
-- =====================================================================

ALTER TABLE org_role
    ADD COLUMN data_scope varchar(32) NOT NULL DEFAULT 'PLANT',
    ADD COLUMN enabled    boolean     NOT NULL DEFAULT true,
    ADD COLUMN sort_no    integer     NOT NULL DEFAULT 0,
    ADD COLUMN builtin    boolean     NOT NULL DEFAULT false;

ALTER TABLE org_role
    ADD CONSTRAINT ck_org_role_data_scope
    CHECK (data_scope IN ('ALL', 'PLANT', 'DEPT_AND_CHILD', 'DEPT', 'SELF', 'CUSTOM'));

UPDATE org_role
   SET builtin = true, data_scope = 'PLANT', enabled = true,
       sort_no = CASE code WHEN 'ADMIN' THEN 10 WHEN 'EDITOR' THEN 20 WHEN 'VIEWER' THEN 30 ELSE sort_no END
 WHERE code IN ('ADMIN', 'EDITOR', 'VIEWER') AND NOT deleted;

-- ---------------------------------------------------------------------
-- 菜单树。DIR 无 perm_code；BUTTON 必填；MENU 的 path / component 必填。
-- ---------------------------------------------------------------------

CREATE TABLE sys_menu (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id     uuid         REFERENCES sys_menu (id),
    type          varchar(16)  NOT NULL CHECK (type IN ('DIR', 'MENU', 'BUTTON')),
    name          varchar(64)  NOT NULL,
    perm_code     varchar(128),
    path          varchar(128),
    component     varchar(128),
    icon          varchar(64),
    sort_no       integer      NOT NULL DEFAULT 0,
    visible       boolean      NOT NULL DEFAULT true,
    enabled       boolean      NOT NULL DEFAULT true,
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    CONSTRAINT ck_sys_menu_perm CHECK (
        (type = 'DIR' AND perm_code IS NULL)
        OR (type = 'MENU')
        OR (type = 'BUTTON' AND perm_code IS NOT NULL)
    ),
    CONSTRAINT ck_sys_menu_route CHECK (
        type <> 'MENU' OR (path IS NOT NULL AND component IS NOT NULL)
    ),
    CONSTRAINT ck_sys_menu_perm_format CHECK (
        perm_code IS NULL
        OR perm_code ~ '^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*){1,3}$'
    )
);
CREATE UNIQUE INDEX uk_sys_menu_perm_code
    ON sys_menu (perm_code) WHERE perm_code IS NOT NULL AND NOT deleted;
CREATE UNIQUE INDEX uk_sys_menu_sibling_path
    ON sys_menu (COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), path)
    WHERE NOT deleted AND type = 'MENU';
CREATE INDEX ix_sys_menu_parent ON sys_menu (parent_id) WHERE NOT deleted;

CREATE TABLE sys_role_menu (
    role_id     uuid        NOT NULL REFERENCES org_role (id),
    menu_id     uuid        NOT NULL REFERENCES sys_menu (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    PRIMARY KEY (role_id, menu_id)
);
CREATE INDEX ix_sys_role_menu_menu ON sys_role_menu (menu_id);

-- 固定 id，便于阅读；页面随后增删改，不依赖这些 id。
INSERT INTO sys_menu (id, parent_id, type, name, perm_code, path, component, sort_no) VALUES
    ('00000000-0000-4000-8000-00000000f201', NULL, 'DIR', '主数据', NULL, NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f202', NULL, 'DIR', '工艺', NULL, NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f203', NULL, 'DIR', '组织', NULL, NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f204', NULL, 'DIR', '系统管理', NULL, NULL, NULL, 40),
    ('00000000-0000-4000-8000-00000000f205', NULL, 'DIR', '配置', NULL, NULL, NULL, 50),

    ('00000000-0000-4000-8000-00000000f210', '00000000-0000-4000-8000-00000000f201', 'MENU', '物料',
        'md:material:list', '/materials', 'md/material', 10),
    ('00000000-0000-4000-8000-00000000f211', '00000000-0000-4000-8000-00000000f210', 'BUTTON', '查看',
        'md:material:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f212', '00000000-0000-4000-8000-00000000f210', 'BUTTON', '新建',
        'md:material:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f213', '00000000-0000-4000-8000-00000000f210', 'BUTTON', '修改',
        'md:material:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f214', '00000000-0000-4000-8000-00000000f210', 'BUTTON', '删除',
        'md:material:delete', NULL, NULL, 40),
    ('00000000-0000-4000-8000-00000000f215', '00000000-0000-4000-8000-00000000f210', 'BUTTON', '发布',
        'md:material:release', NULL, NULL, 50),
    ('00000000-0000-4000-8000-00000000f216', '00000000-0000-4000-8000-00000000f210', 'BUTTON', '作废',
        'md:material:obsolete', NULL, NULL, 60),
    ('00000000-0000-4000-8000-00000000f217', '00000000-0000-4000-8000-00000000f210', 'BUTTON', '维护共享主数据',
        'md:shared:update', NULL, NULL, 70),

    ('00000000-0000-4000-8000-00000000f220', '00000000-0000-4000-8000-00000000f201', 'MENU', '计量单位',
        'md:uom:list', '/uoms', 'md/uom', 20),
    ('00000000-0000-4000-8000-00000000f221', '00000000-0000-4000-8000-00000000f220', 'BUTTON', '查看',
        'md:uom:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f222', '00000000-0000-4000-8000-00000000f220', 'BUTTON', '新建',
        'md:uom:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f223', '00000000-0000-4000-8000-00000000f220', 'BUTTON', '修改',
        'md:uom:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f224', '00000000-0000-4000-8000-00000000f220', 'BUTTON', '删除',
        'md:uom:delete', NULL, NULL, 40),

    ('00000000-0000-4000-8000-00000000f230', '00000000-0000-4000-8000-00000000f202', 'MENU', '工序',
        'rtg:operation:list', '/operations', 'rtg/operation', 10),
    ('00000000-0000-4000-8000-00000000f231', '00000000-0000-4000-8000-00000000f230', 'BUTTON', '查看',
        'rtg:operation:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f232', '00000000-0000-4000-8000-00000000f230', 'BUTTON', '新建',
        'rtg:operation:create', NULL, NULL, 20),

    ('00000000-0000-4000-8000-00000000f240', '00000000-0000-4000-8000-00000000f202', 'MENU', '工艺路线',
        'rtg:routing:list', '/routings', 'rtg/routing', 20),
    ('00000000-0000-4000-8000-00000000f241', '00000000-0000-4000-8000-00000000f240', 'BUTTON', '查看',
        'rtg:routing:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f242', '00000000-0000-4000-8000-00000000f240', 'BUTTON', '新建',
        'rtg:routing:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f243', '00000000-0000-4000-8000-00000000f240', 'BUTTON', '修改',
        'rtg:routing:update', NULL, NULL, 30),

    ('00000000-0000-4000-8000-00000000f250', '00000000-0000-4000-8000-00000000f203', 'MENU', '工厂',
        'org:plant:list', '/plants', 'org/plant', 10),
    ('00000000-0000-4000-8000-00000000f251', '00000000-0000-4000-8000-00000000f250', 'BUTTON', '查看',
        'org:plant:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f252', '00000000-0000-4000-8000-00000000f250', 'BUTTON', '新建',
        'org:plant:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f253', '00000000-0000-4000-8000-00000000f250', 'BUTTON', '修改',
        'org:plant:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f254', '00000000-0000-4000-8000-00000000f250', 'BUTTON', '删除',
        'org:plant:delete', NULL, NULL, 40),

    ('00000000-0000-4000-8000-00000000f260', '00000000-0000-4000-8000-00000000f204', 'MENU', '用户',
        'sys:user:list', '/users', 'sys/user', 10),
    ('00000000-0000-4000-8000-00000000f261', '00000000-0000-4000-8000-00000000f260', 'BUTTON', '查看',
        'sys:user:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f262', '00000000-0000-4000-8000-00000000f260', 'BUTTON', '新建',
        'sys:user:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f263', '00000000-0000-4000-8000-00000000f260', 'BUTTON', '修改',
        'sys:user:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f264', '00000000-0000-4000-8000-00000000f260', 'BUTTON', '删除',
        'sys:user:delete', NULL, NULL, 40),
    ('00000000-0000-4000-8000-00000000f265', '00000000-0000-4000-8000-00000000f260', 'BUTTON', '启用',
        'sys:user:enable', NULL, NULL, 50),
    ('00000000-0000-4000-8000-00000000f266', '00000000-0000-4000-8000-00000000f260', 'BUTTON', '重置密码',
        'sys:user:reset-pwd', NULL, NULL, 60),
    ('00000000-0000-4000-8000-00000000f267', '00000000-0000-4000-8000-00000000f260', 'BUTTON', '授权',
        'sys:user:grant', NULL, NULL, 70),

    ('00000000-0000-4000-8000-00000000f270', '00000000-0000-4000-8000-00000000f204', 'MENU', '角色',
        'sys:role:list', '/roles', 'sys/role', 20),
    ('00000000-0000-4000-8000-00000000f271', '00000000-0000-4000-8000-00000000f270', 'BUTTON', '查看',
        'sys:role:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f272', '00000000-0000-4000-8000-00000000f270', 'BUTTON', '新建',
        'sys:role:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f273', '00000000-0000-4000-8000-00000000f270', 'BUTTON', '修改',
        'sys:role:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f274', '00000000-0000-4000-8000-00000000f270', 'BUTTON', '删除',
        'sys:role:delete', NULL, NULL, 40),
    ('00000000-0000-4000-8000-00000000f275', '00000000-0000-4000-8000-00000000f270', 'BUTTON', '授权',
        'sys:role:grant', NULL, NULL, 50),

    ('00000000-0000-4000-8000-00000000f280', '00000000-0000-4000-8000-00000000f204', 'MENU', '菜单',
        'sys:menu:list', '/menus', 'sys/menu', 30),
    ('00000000-0000-4000-8000-00000000f281', '00000000-0000-4000-8000-00000000f280', 'BUTTON', '查看',
        'sys:menu:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f282', '00000000-0000-4000-8000-00000000f280', 'BUTTON', '新建',
        'sys:menu:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f283', '00000000-0000-4000-8000-00000000f280', 'BUTTON', '修改',
        'sys:menu:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f284', '00000000-0000-4000-8000-00000000f280', 'BUTTON', '删除',
        'sys:menu:delete', NULL, NULL, 40),

    ('00000000-0000-4000-8000-00000000f290', '00000000-0000-4000-8000-00000000f205', 'MENU', '自定义字段',
        'cfg:field-schema:list', '/custom-field-schemas', 'cfg/field-schema', 10),
    ('00000000-0000-4000-8000-00000000f291', '00000000-0000-4000-8000-00000000f290', 'BUTTON', '查看',
        'cfg:field-schema:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f292', '00000000-0000-4000-8000-00000000f290', 'BUTTON', '修改',
        'cfg:field-schema:update', NULL, NULL, 20);

-- ADMIN：全部权限点，数据范围 PLANT（列默认值）。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM org_role r
  JOIN sys_menu m ON m.perm_code IS NOT NULL AND NOT m.deleted
 WHERE r.code = 'ADMIN' AND NOT r.deleted;

-- VIEWER：读本厂可见的主数据、工艺、工厂和自定义字段，不含用户/角色/菜单管理。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM org_role r
  JOIN sys_menu m ON NOT m.deleted AND m.perm_code IN (
        'md:material:list', 'md:material:view',
        'md:uom:list', 'md:uom:view',
        'rtg:operation:list', 'rtg:operation:view',
        'rtg:routing:list', 'rtg:routing:view',
        'org:plant:list', 'org:plant:view',
        'cfg:field-schema:list', 'cfg:field-schema:view'
  )
 WHERE r.code = 'VIEWER' AND NOT r.deleted;

-- EDITOR：VIEWER 加上本厂私有主数据与厂扩展的维护。共享主数据仍只给 ADMIN。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM org_role r
  JOIN sys_menu m ON NOT m.deleted AND m.perm_code IN (
        'md:material:list', 'md:material:view',
        'md:material:create', 'md:material:update', 'md:material:delete',
        'md:material:release', 'md:material:obsolete',
        'md:uom:list', 'md:uom:view',
        'rtg:operation:list', 'rtg:operation:view', 'rtg:operation:create',
        'rtg:routing:list', 'rtg:routing:view', 'rtg:routing:create', 'rtg:routing:update',
        'org:plant:list', 'org:plant:view',
        'cfg:field-schema:list', 'cfg:field-schema:view'
  )
 WHERE r.code = 'EDITOR' AND NOT r.deleted;
