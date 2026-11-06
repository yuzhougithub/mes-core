-- =====================================================================
-- 草案，评审后由后端移入 apps/api/src/main/resources/db/migration/。
-- 不要在评审前放进 migration 目录，避免 Flyway 自动执行。
--
-- V1 主数据竖切。与 docs/v1-data-model.md 一致。PostgreSQL 17。
-- 约定：
--   - 主键 uuid，默认 gen_random_uuid()（PG 13+ 内置）。
--   - 审计列 created_at / created_by / updated_at / updated_by；
--     *_by 为 org_person.id 或认证主体 id，auth 未落地前可空，不建外键。
--   - lock_version：MyBatis-Plus @Version 乐观锁。
--   - deleted：MyBatis-Plus @TableLogic 逻辑删除，仅聚合根有；唯一索引带 WHERE NOT deleted。
--   - plant_id：共享型表可空（空 = 全厂共享，非空 = 该厂私有）；
--     厂自有表非空。
--   - 枚举用 varchar + CHECK；字典值用 varchar，不建外键。
--   - 自定义字段值 ext_values jsonb，键为字段 key。
-- =====================================================================

-- ---------------------------------------------------------------------
-- org：工厂、车间、工作中心、人员、任职、角色、班组
-- ---------------------------------------------------------------------

CREATE TABLE org_plant (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          varchar(32)  NOT NULL,
    name          varchar(128) NOT NULL,
    timezone      varchar(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    enabled       boolean      NOT NULL DEFAULT true,
    remark        varchar(500),
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_org_plant_code ON org_plant (code) WHERE NOT deleted;

CREATE TABLE org_workshop (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id      uuid         NOT NULL REFERENCES org_plant (id),
    code          varchar(32)  NOT NULL,
    name          varchar(128) NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    remark        varchar(500),
    ext_values    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_org_workshop_code ON org_workshop (plant_id, code) WHERE NOT deleted;

CREATE TABLE org_work_center (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id          uuid         NOT NULL REFERENCES org_plant (id),
    workshop_id       uuid         NOT NULL REFERENCES org_workshop (id),
    code              varchar(32)  NOT NULL,
    name              varchar(128) NOT NULL,
    work_center_type  varchar(32),                       -- 字典 work_center_type
    enabled           boolean      NOT NULL DEFAULT true,
    remark            varchar(500),
    ext_values        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    lock_version      integer      NOT NULL DEFAULT 0,
    deleted           boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        uuid
);
CREATE UNIQUE INDEX uk_org_work_center_code ON org_work_center (plant_id, code) WHERE NOT deleted;
CREATE INDEX ix_org_work_center_workshop ON org_work_center (workshop_id);

-- 人员是全局身份；在某厂工作通过 org_person_plant（工厂任职）表达。
CREATE TABLE org_person (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          varchar(32)  NOT NULL,                 -- 工号，全局唯一
    name          varchar(64)  NOT NULL,
    mobile        varchar(32),
    email         varchar(128),
    enabled       boolean      NOT NULL DEFAULT true,
    remark        varchar(500),
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_org_person_code ON org_person (code) WHERE NOT deleted;

-- 工厂任职：人员在某厂的扩展记录，承载该厂自定义字段值（entity_type = person）。
CREATE TABLE org_person_plant (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id     uuid         NOT NULL REFERENCES org_person (id),
    plant_id      uuid         NOT NULL REFERENCES org_plant (id),
    workshop_id   uuid         REFERENCES org_workshop (id),
    job_title     varchar(64),
    active        boolean      NOT NULL DEFAULT true,
    ext_values    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    lock_version  integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    CONSTRAINT uk_org_person_plant UNIQUE (person_id, plant_id)
);
CREATE INDEX ix_org_person_plant_plant ON org_person_plant (plant_id);

-- 简单角色：全局定义，按任职授予。不含权限点（auth 后续）。
CREATE TABLE org_role (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          varchar(32)  NOT NULL,
    name          varchar(64)  NOT NULL,
    remark        varchar(500),
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_org_role_code ON org_role (code) WHERE NOT deleted;

CREATE TABLE org_person_plant_role (
    person_plant_id  uuid        NOT NULL REFERENCES org_person_plant (id) ON DELETE CASCADE,
    role_id          uuid        NOT NULL REFERENCES org_role (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    PRIMARY KEY (person_plant_id, role_id)
);

CREATE TABLE org_team (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id          uuid         NOT NULL REFERENCES org_plant (id),
    workshop_id       uuid         REFERENCES org_workshop (id),
    code              varchar(32)  NOT NULL,
    name              varchar(64)  NOT NULL,
    leader_person_id  uuid         REFERENCES org_person (id),
    enabled           boolean      NOT NULL DEFAULT true,
    remark            varchar(500),
    lock_version      integer      NOT NULL DEFAULT 0,
    deleted           boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        uuid
);
CREATE UNIQUE INDEX uk_org_team_code ON org_team (plant_id, code) WHERE NOT deleted;

CREATE TABLE org_team_member (
    team_id     uuid        NOT NULL REFERENCES org_team (id) ON DELETE CASCADE,
    person_id   uuid        NOT NULL REFERENCES org_person (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    PRIMARY KEY (team_id, person_id)
);
CREATE INDEX ix_org_team_member_person ON org_team_member (person_id);

-- ---------------------------------------------------------------------
-- material：物料分类、计量单位、物料、BOM
-- ---------------------------------------------------------------------

CREATE TABLE mat_category (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id     uuid         REFERENCES mat_category (id),
    code          varchar(32)  NOT NULL,
    name          varchar(128) NOT NULL,
    sort_no       integer      NOT NULL DEFAULT 0,
    enabled       boolean      NOT NULL DEFAULT true,
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_mat_category_code ON mat_category (code) WHERE NOT deleted;
CREATE INDEX ix_mat_category_parent ON mat_category (parent_id);

CREATE TABLE mat_uom (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          varchar(16)  NOT NULL,
    name          varchar(32)  NOT NULL,
    decimals      smallint     NOT NULL DEFAULT 0 CHECK (decimals BETWEEN 0 AND 6),
    enabled       boolean      NOT NULL DEFAULT true,
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_mat_uom_code ON mat_uom (code) WHERE NOT deleted;

-- 物料：共享型。plant_id 空 = 共享；非空 = 该厂私有。状态即生命周期，不分多版本。
CREATE TABLE mat_material (
    id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id       uuid          REFERENCES org_plant (id),
    code           varchar(64)   NOT NULL,
    name           varchar(200)  NOT NULL,
    spec           varchar(500),
    material_type  varchar(32)   NOT NULL,              -- 字典 material_type
    category_id    uuid          REFERENCES mat_category (id),
    uom_id         uuid          NOT NULL REFERENCES mat_uom (id),
    drawing_no     varchar(64),
    status         varchar(16)   NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT', 'RELEASED', 'OBSOLETE')),
    released_at    timestamptz,
    released_by    uuid,
    remark         varchar(500),
    lock_version   integer       NOT NULL DEFAULT 0,
    deleted        boolean       NOT NULL DEFAULT false,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     uuid
);
CREATE UNIQUE INDEX uk_mat_material_code ON mat_material (code) WHERE NOT deleted;
CREATE INDEX ix_mat_material_plant ON mat_material (plant_id);
CREATE INDEX ix_mat_material_category ON mat_material (category_id);

-- 物料厂级扩展：每厂每物料至多一行。行不存在 = 本厂启用、无扩展值。
CREATE TABLE mat_material_plant_ext (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id   uuid         NOT NULL REFERENCES mat_material (id),
    plant_id      uuid         NOT NULL REFERENCES org_plant (id),
    enabled       boolean      NOT NULL DEFAULT true,
    ext_values    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    lock_version  integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    CONSTRAINT uk_mat_material_plant_ext UNIQUE (material_id, plant_id)
);
CREATE INDEX ix_mat_material_plant_ext_plant ON mat_material_plant_ext (plant_id);

-- BOM：一行 = 父件的一个版本。plant_id 空 = 共享版本；非空 = 该厂专用版本。
CREATE TABLE mat_bom (
    id            uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id   uuid           NOT NULL REFERENCES mat_material (id),
    plant_id      uuid           REFERENCES org_plant (id),
    version_no    varchar(16)    NOT NULL,
    base_qty      numeric(18, 6) NOT NULL DEFAULT 1 CHECK (base_qty > 0),
    status        varchar(16)    NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT', 'RELEASED', 'OBSOLETE')),
    released_at   timestamptz,
    released_by   uuid,
    remark        varchar(500),
    lock_version  integer        NOT NULL DEFAULT 0,
    deleted       boolean        NOT NULL DEFAULT false,
    created_at    timestamptz    NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_mat_bom_version ON mat_bom (material_id, plant_id, version_no)
    NULLS NOT DISTINCT WHERE NOT deleted;
-- 同一父件、同一范围同一时刻至多一个发布版本。
CREATE UNIQUE INDEX uk_mat_bom_released ON mat_bom (material_id, plant_id)
    NULLS NOT DISTINCT WHERE status = 'RELEASED' AND NOT deleted;

CREATE TABLE mat_bom_line (
    id                     uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    bom_id                 uuid           NOT NULL REFERENCES mat_bom (id) ON DELETE CASCADE,
    line_no                integer        NOT NULL,
    component_material_id  uuid           NOT NULL REFERENCES mat_material (id),
    qty                    numeric(18, 6) NOT NULL CHECK (qty > 0),
    uom_id                 uuid           NOT NULL REFERENCES mat_uom (id),
    scrap_rate             numeric(7, 4)  NOT NULL DEFAULT 0 CHECK (scrap_rate >= 0 AND scrap_rate < 1),
    operation_id           uuid,                          -- 消耗工序，可空；外键见 routing 段末尾
    remark                 varchar(500),
    CONSTRAINT uk_mat_bom_line_no UNIQUE (bom_id, line_no)
);
CREATE INDEX ix_mat_bom_line_component ON mat_bom_line (component_material_id);

-- ---------------------------------------------------------------------
-- routing：工序、工艺路线（版本 + 返工分支）
-- ---------------------------------------------------------------------

CREATE TABLE rtg_operation (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id        uuid         REFERENCES org_plant (id),
    code            varchar(32)  NOT NULL,
    name            varchar(128) NOT NULL,
    operation_type  varchar(32),                         -- 字典 operation_type
    std_time_sec    numeric(12, 2) CHECK (std_time_sec >= 0),
    enabled         boolean      NOT NULL DEFAULT true,
    remark          varchar(500),
    lock_version    integer      NOT NULL DEFAULT 0,
    deleted         boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid
);
CREATE UNIQUE INDEX uk_rtg_operation_code ON rtg_operation (code) WHERE NOT deleted;
CREATE INDEX ix_rtg_operation_plant ON rtg_operation (plant_id);

CREATE TABLE rtg_operation_plant_ext (
    id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id           uuid         NOT NULL REFERENCES rtg_operation (id),
    plant_id               uuid         NOT NULL REFERENCES org_plant (id),
    enabled                boolean      NOT NULL DEFAULT true,
    default_work_center_id uuid         REFERENCES org_work_center (id),
    std_time_sec           numeric(12, 2) CHECK (std_time_sec >= 0),   -- 覆盖共享值，空 = 不覆盖
    ext_values             jsonb        NOT NULL DEFAULT '{}'::jsonb,
    lock_version           integer      NOT NULL DEFAULT 0,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    updated_by             uuid,
    CONSTRAINT uk_rtg_operation_plant_ext UNIQUE (operation_id, plant_id)
);

ALTER TABLE mat_bom_line
    ADD CONSTRAINT fk_mat_bom_line_operation FOREIGN KEY (operation_id) REFERENCES rtg_operation (id);

-- 工艺路线：一行 = 路线编码的一个版本。
CREATE TABLE rtg_routing (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id      uuid         REFERENCES org_plant (id),
    code          varchar(64)  NOT NULL,
    version_no    varchar(16)  NOT NULL,
    name          varchar(200) NOT NULL,
    material_id   uuid         REFERENCES mat_material (id),   -- 适用产品，可空 = 通用路线
    status        varchar(16)  NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT', 'RELEASED', 'OBSOLETE')),
    released_at   timestamptz,
    released_by   uuid,
    remark        varchar(500),
    layout_json   jsonb,                                 -- 画布坐标，可空；不进业务校验
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_rtg_routing_version ON rtg_routing (code, plant_id, version_no)
    NULLS NOT DISTINCT WHERE NOT deleted;
CREATE UNIQUE INDEX uk_rtg_routing_released ON rtg_routing (code, plant_id)
    NULLS NOT DISTINCT WHERE status = 'RELEASED' AND NOT deleted;
CREATE INDEX ix_rtg_routing_material ON rtg_routing (material_id);

-- 工序节点。主路线 = is_rework = false 的节点按 step_no 升序。
CREATE TABLE rtg_routing_step (
    id              uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    routing_id      uuid           NOT NULL REFERENCES rtg_routing (id) ON DELETE CASCADE,
    step_no         integer        NOT NULL CHECK (step_no > 0),
    operation_id    uuid           NOT NULL REFERENCES rtg_operation (id),
    work_center_id  uuid           REFERENCES org_work_center (id),  -- 仅厂专用路线可填
    is_rework       boolean        NOT NULL DEFAULT false,
    std_time_sec    numeric(12, 2) CHECK (std_time_sec >= 0),
    remark          varchar(500),
    CONSTRAINT uk_rtg_routing_step_no UNIQUE (routing_id, step_no)
);

-- 返工分支边：REWORK 从主路线节点出到返工节点（或回到前序主路线节点）；RETURN 从返工节点回主路线。
CREATE TABLE rtg_routing_edge (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    routing_id      uuid         NOT NULL REFERENCES rtg_routing (id) ON DELETE CASCADE,
    from_step_id    uuid         NOT NULL REFERENCES rtg_routing_step (id) ON DELETE CASCADE,
    to_step_id      uuid         NOT NULL REFERENCES rtg_routing_step (id) ON DELETE CASCADE,
    edge_type       varchar(16)  NOT NULL CHECK (edge_type IN ('REWORK', 'RETURN')),
    defect_code_id  uuid,                                -- 触发不良，可空；外键见 config 段末尾
    remark          varchar(500),
    CONSTRAINT ck_rtg_routing_edge_self CHECK (from_step_id <> to_step_id),
    CONSTRAINT uk_rtg_routing_edge UNIQUE NULLS NOT DISTINCT (from_step_id, to_step_id, edge_type, defect_code_id)
);
CREATE INDEX ix_rtg_routing_edge_routing ON rtg_routing_edge (routing_id);

-- ---------------------------------------------------------------------
-- resource：设备（厂自有）
-- ---------------------------------------------------------------------

CREATE TABLE res_equipment (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id         uuid         NOT NULL REFERENCES org_plant (id),
    work_center_id   uuid         REFERENCES org_work_center (id),
    code             varchar(64)  NOT NULL,
    name             varchar(128) NOT NULL,
    equipment_type   varchar(32),                        -- 字典 equipment_type
    model            varchar(128),
    manufacturer     varchar(128),
    serial_no        varchar(128),
    commissioned_on  date,
    status           varchar(16)  NOT NULL DEFAULT 'IN_SERVICE'
                     CHECK (status IN ('IN_SERVICE', 'IDLE', 'MAINTENANCE', 'SCRAPPED')),
    remark           varchar(500),
    ext_values       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    lock_version     integer      NOT NULL DEFAULT 0,
    deleted          boolean      NOT NULL DEFAULT false,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       uuid
);
CREATE UNIQUE INDEX uk_res_equipment_code ON res_equipment (plant_id, code) WHERE NOT deleted;
CREATE INDEX ix_res_equipment_work_center ON res_equipment (work_center_id);

-- ---------------------------------------------------------------------
-- config：自定义字段、数据字典、不良/原因代码、编码规则、文件
-- ---------------------------------------------------------------------

-- 自定义字段配置：每厂每实体类型一份，schema 形状见 docs/architecture.md「动态表单」。
CREATE TABLE cfg_custom_field_schema (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id      uuid         NOT NULL REFERENCES org_plant (id),
    entity_type   varchar(32)  NOT NULL
                  CHECK (entity_type IN ('material', 'operation', 'person', 'workshop', 'work_center', 'equipment')),
    schema        jsonb        NOT NULL DEFAULT '{"schemaVersion": 1, "fields": []}'::jsonb
                  CHECK (jsonb_typeof(schema -> 'fields') = 'array'),
    lock_version  integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    CONSTRAINT uk_cfg_custom_field_schema UNIQUE (plant_id, entity_type)
);

CREATE TABLE cfg_dict_type (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          varchar(64)  NOT NULL,
    name          varchar(128) NOT NULL,
    system        boolean      NOT NULL DEFAULT false,   -- 系统字典：不可删，可增项
    remark        varchar(500),
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_cfg_dict_type_code ON cfg_dict_type (code) WHERE NOT deleted;

-- 字典项：plant_id 空 = 共享项；非空 = 该厂追加项。
CREATE TABLE cfg_dict_item (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    dict_type_id  uuid         NOT NULL REFERENCES cfg_dict_type (id),
    plant_id      uuid         REFERENCES org_plant (id),
    value         varchar(64)  NOT NULL,
    label         varchar(128) NOT NULL,
    sort_no       integer      NOT NULL DEFAULT 0,
    enabled       boolean      NOT NULL DEFAULT true,
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
-- value 在字典内全局唯一（共享项与厂追加项不重名）。
CREATE UNIQUE INDEX uk_cfg_dict_item_value ON cfg_dict_item (dict_type_id, value) WHERE NOT deleted;

CREATE TABLE cfg_defect_code (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id         uuid         REFERENCES org_plant (id),
    code             varchar(32)  NOT NULL,
    name             varchar(128) NOT NULL,
    defect_category  varchar(32),                        -- 字典 defect_category
    severity         varchar(16)  NOT NULL DEFAULT 'MINOR'
                     CHECK (severity IN ('CRITICAL', 'MAJOR', 'MINOR')),
    enabled          boolean      NOT NULL DEFAULT true,
    remark           varchar(500),
    lock_version     integer      NOT NULL DEFAULT 0,
    deleted          boolean      NOT NULL DEFAULT false,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       uuid
);
CREATE UNIQUE INDEX uk_cfg_defect_code_code ON cfg_defect_code (code) WHERE NOT deleted;

CREATE TABLE cfg_reason_code (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id      uuid         REFERENCES org_plant (id),
    code          varchar(32)  NOT NULL,
    name          varchar(128) NOT NULL,
    reason_type   varchar(32)  NOT NULL,                 -- 字典 reason_type：报废/返工/停机…
    enabled       boolean      NOT NULL DEFAULT true,
    remark        varchar(500),
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_cfg_reason_code_code ON cfg_reason_code (code) WHERE NOT deleted;

ALTER TABLE rtg_routing_edge
    ADD CONSTRAINT fk_rtg_routing_edge_defect FOREIGN KEY (defect_code_id) REFERENCES cfg_defect_code (id);

-- 编码规则：前缀 + 日期段 + 定长流水号，如 MAT-20261106-0001。
-- plant_id 空 = 共享规则；非空 = 该厂规则，优先于共享规则。
CREATE TABLE cfg_code_rule (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id      uuid         REFERENCES org_plant (id),
    entity_type   varchar(32)  NOT NULL
                  CHECK (entity_type IN ('material', 'bom', 'routing', 'operation', 'equipment',
                                         'person', 'workshop', 'work_center', 'team',
                                         'defect_code', 'reason_code')),
    prefix        varchar(16)  NOT NULL DEFAULT '',
    date_pattern  varchar(8)   NOT NULL DEFAULT 'NONE'
                  CHECK (date_pattern IN ('NONE', 'yyyy', 'yyyyMM', 'yyyyMMdd')),
    separator     varchar(2)   NOT NULL DEFAULT '-',
    seq_length    smallint     NOT NULL DEFAULT 4 CHECK (seq_length BETWEEN 1 AND 12),
    reset_cycle   varchar(8)   NOT NULL DEFAULT 'NEVER'
                  CHECK (reset_cycle IN ('NEVER', 'YEAR', 'MONTH', 'DAY')),
    enabled       boolean      NOT NULL DEFAULT true,
    lock_version  integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    CONSTRAINT uk_cfg_code_rule UNIQUE NULLS NOT DISTINCT (entity_type, plant_id)
);

-- 流水计数。取号：INSERT ... ON CONFLICT (rule_id, period_key) DO UPDATE SET current_value = current_value + 1 RETURNING current_value。
CREATE TABLE cfg_code_rule_seq (
    rule_id        uuid         NOT NULL REFERENCES cfg_code_rule (id) ON DELETE CASCADE,
    period_key     varchar(8)   NOT NULL,                -- '-'（NEVER）、'2026'、'202611'、'20261106'
    current_value  bigint       NOT NULL DEFAULT 0,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (rule_id, period_key)
);

-- 上传文件元数据。image/file 自定义字段值 = object_key。
CREATE TABLE cfg_file_object (
    id            uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id      uuid          REFERENCES org_plant (id),
    object_key    varchar(512)  NOT NULL,
    file_name     varchar(255)  NOT NULL,
    content_type  varchar(128)  NOT NULL,
    size_bytes    bigint        NOT NULL CHECK (size_bytes >= 0),
    created_at    timestamptz   NOT NULL DEFAULT now(),
    created_by    uuid,
    CONSTRAINT uk_cfg_file_object_key UNIQUE (object_key)
);

-- ---------------------------------------------------------------------
-- 种子：系统字典类型与少量默认项
-- ---------------------------------------------------------------------

INSERT INTO cfg_dict_type (code, name, system) VALUES
    ('material_type',    '物料类型',   true),
    ('work_center_type', '工作中心类型', true),
    ('operation_type',   '工序类型',   true),
    ('equipment_type',   '设备类型',   true),
    ('defect_category',  '不良类别',   true),
    ('reason_type',      '原因类型',   true);

INSERT INTO cfg_dict_item (dict_type_id, value, label, sort_no)
SELECT t.id, v.value, v.label, v.sort_no
FROM cfg_dict_type t
JOIN (VALUES
    ('material_type', 'RAW',      '原材料', 10),
    ('material_type', 'SEMI',     '半成品', 20),
    ('material_type', 'FINISHED', '成品',   30),
    ('material_type', 'AUX',      '辅料',   40),
    ('reason_type',   'SCRAP',    '报废',   10),
    ('reason_type',   'REWORK',   '返工',   20),
    ('reason_type',   'DOWNTIME', '停机',   30)
) AS v (type_code, value, label, sort_no) ON v.type_code = t.code;

-- 角色种子：三档，见 docs/v1-master-data-overview.md「认证、角色与当前工厂」。
INSERT INTO org_role (code, name, remark) VALUES
    ('VIEWER', '只读',   '读本厂可见数据，导出'),
    ('EDITOR', '维护',   '维护本厂私有数据与厂扩展值，导入'),
    ('ADMIN',  '管理员', '维护共享主数据与配置');
