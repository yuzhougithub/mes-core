-- =====================================================================
-- V6：F3 部门、岗位与数据范围（docs/v1-foundation.md §3.1 / §4.3 / §5）。
-- 部门树按工厂；plant_id 空 = 集团级部门。岗位全局。
-- 人员主部门 org_person.dept_id，岗位多对多 org_person_post。
-- 角色 CUSTOM 的部门集合 sys_role_data_dept。
-- 需要 PostgreSQL >= 15；目标 PostgreSQL 17。
-- =====================================================================

CREATE TABLE org_dept (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id          uuid         REFERENCES org_plant (id),
    parent_id         uuid         REFERENCES org_dept (id),
    code              varchar(32)  NOT NULL,
    name              varchar(64)  NOT NULL,
    ancestors         text         NOT NULL,
    leader_person_id  uuid         REFERENCES org_person (id),
    sort_no           integer      NOT NULL DEFAULT 0,
    enabled           boolean      NOT NULL DEFAULT true,
    lock_version      integer      NOT NULL DEFAULT 0,
    deleted           boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        uuid,
    CONSTRAINT ck_org_dept_ancestors CHECK (
        ancestors ~ '^(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})+/$'
    )
);
-- 规格未写编码唯一。与其他 org 表一致：同工厂（集团级 plant_id 空单独成组）内编码不重复。
CREATE UNIQUE INDEX uk_org_dept_code
    ON org_dept (plant_id, code) NULLS NOT DISTINCT
    WHERE NOT deleted;
CREATE INDEX ix_org_dept_parent ON org_dept (parent_id) WHERE NOT deleted;
CREATE INDEX ix_org_dept_plant ON org_dept (plant_id) WHERE NOT deleted;
CREATE INDEX ix_org_dept_ancestors ON org_dept (ancestors text_pattern_ops) WHERE NOT deleted;

CREATE TABLE org_post (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          varchar(32)  NOT NULL,
    name          varchar(64)  NOT NULL,
    sort_no       integer      NOT NULL DEFAULT 0,
    enabled       boolean      NOT NULL DEFAULT true,
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_org_post_code ON org_post (code) WHERE NOT deleted;

ALTER TABLE org_person
    ADD COLUMN dept_id uuid REFERENCES org_dept (id);
CREATE INDEX ix_org_person_dept ON org_person (dept_id) WHERE dept_id IS NOT NULL AND NOT deleted;

CREATE TABLE org_person_post (
    person_id   uuid        NOT NULL REFERENCES org_person (id),
    post_id     uuid        NOT NULL REFERENCES org_post (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    PRIMARY KEY (person_id, post_id)
);
CREATE INDEX ix_org_person_post_post ON org_person_post (post_id);

CREATE TABLE sys_role_data_dept (
    role_id     uuid        NOT NULL REFERENCES org_role (id),
    dept_id     uuid        NOT NULL REFERENCES org_dept (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    PRIMARY KEY (role_id, dept_id)
);
CREATE INDEX ix_sys_role_data_dept_dept ON sys_role_data_dept (dept_id);

-- 父部门必须与本部门同工厂（含双方都是集团级）。成环由推迟触发器拒绝。
CREATE FUNCTION mes_dept_parent_same_plant() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.deleted OR NEW.parent_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NOT EXISTS (
        SELECT 1
          FROM org_dept parent
         WHERE parent.id = NEW.parent_id
           AND NOT parent.deleted
           AND parent.plant_id IS NOT DISTINCT FROM NEW.plant_id
    ) THEN
        RAISE EXCEPTION 'org_dept parent must belong to the same plant' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_org_dept_parent_same_plant
    BEFORE INSERT OR UPDATE OF parent_id, plant_id, deleted ON org_dept
    FOR EACH ROW
    EXECUTE FUNCTION mes_dept_parent_same_plant();

CREATE FUNCTION mes_dept_no_cycle() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        WITH RECURSIVE ancestors AS (
            SELECT id, parent_id, ARRAY[id] AS path
              FROM org_dept
             WHERE NOT deleted AND parent_id IS NOT NULL
            UNION ALL
            SELECT a.id, d.parent_id, a.path || d.id
              FROM ancestors a
              JOIN org_dept d ON d.id = a.parent_id AND NOT d.deleted
             WHERE NOT d.id = ANY (a.path)
        )
        SELECT 1
          FROM ancestors a
         WHERE a.parent_id IS NOT NULL
           AND a.parent_id = ANY (a.path)
    ) THEN
        RAISE EXCEPTION 'org_dept contains a cycle' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_org_dept_no_cycle
    AFTER INSERT OR UPDATE OR DELETE ON org_dept
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION mes_dept_no_cycle();

-- ---------------------------------------------------------------------
-- 菜单种子：部门、岗位。ADMIN 持有全部新权限点。EDITOR / VIEWER 规格未列，不授。
-- ---------------------------------------------------------------------

INSERT INTO sys_menu (id, parent_id, type, name, perm_code, path, component, sort_no) VALUES
    ('00000000-0000-4000-8000-00000000f2a0', '00000000-0000-4000-8000-00000000f203', 'MENU', '部门',
        'org:dept:list', '/depts', 'org/dept', 20),
    ('00000000-0000-4000-8000-00000000f2a1', '00000000-0000-4000-8000-00000000f2a0', 'BUTTON', '查看',
        'org:dept:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f2a2', '00000000-0000-4000-8000-00000000f2a0', 'BUTTON', '新建',
        'org:dept:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f2a3', '00000000-0000-4000-8000-00000000f2a0', 'BUTTON', '修改',
        'org:dept:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f2a4', '00000000-0000-4000-8000-00000000f2a0', 'BUTTON', '删除',
        'org:dept:delete', NULL, NULL, 40),

    ('00000000-0000-4000-8000-00000000f2b0', '00000000-0000-4000-8000-00000000f203', 'MENU', '岗位',
        'org:post:list', '/posts', 'org/post', 30),
    ('00000000-0000-4000-8000-00000000f2b1', '00000000-0000-4000-8000-00000000f2b0', 'BUTTON', '查看',
        'org:post:view', NULL, NULL, 10),
    ('00000000-0000-4000-8000-00000000f2b2', '00000000-0000-4000-8000-00000000f2b0', 'BUTTON', '新建',
        'org:post:create', NULL, NULL, 20),
    ('00000000-0000-4000-8000-00000000f2b3', '00000000-0000-4000-8000-00000000f2b0', 'BUTTON', '修改',
        'org:post:update', NULL, NULL, 30),
    ('00000000-0000-4000-8000-00000000f2b4', '00000000-0000-4000-8000-00000000f2b0', 'BUTTON', '删除',
        'org:post:delete', NULL, NULL, 40);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM org_role r
  JOIN sys_menu m ON NOT m.deleted AND m.perm_code IN (
        'org:dept:list', 'org:dept:view', 'org:dept:create', 'org:dept:update', 'org:dept:delete',
        'org:post:list', 'org:post:view', 'org:post:create', 'org:post:update', 'org:post:delete'
  )
 WHERE r.code = 'ADMIN' AND NOT r.deleted;
