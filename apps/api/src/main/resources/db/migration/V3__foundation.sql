-- =====================================================================
-- V3：F1 认证与用户（docs/v1-foundation.md §3.2 / §4.1）。
-- 账号与 org_person 严格一对一；任职沿用 org_person_plant 并强制恰好一个默认工厂；
-- 口令哈希在 sys_user.password_hash（BCrypt）；刷新令牌只存 SHA-256 十六进制。
-- 不在本迁移建菜单 / 部门 / 日志表（F2 及以后）。
-- 需要 PostgreSQL >= 15；目标 PostgreSQL 17。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 系统参数（密码策略、锁定、会话时长）。列名 config_key 对应规格里的 key。
-- ---------------------------------------------------------------------

CREATE TABLE sys_config (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key    varchar(128) NOT NULL,
    value         text         NOT NULL,
    value_type    varchar(16)  NOT NULL CHECK (value_type IN ('STRING', 'INT', 'BOOL', 'JSON')),
    name          varchar(128) NOT NULL,
    builtin       boolean      NOT NULL DEFAULT false,
    remark        varchar(500),
    lock_version  integer      NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid
);
CREATE UNIQUE INDEX uk_sys_config_key ON sys_config (config_key) WHERE NOT deleted;

INSERT INTO sys_config (config_key, value, value_type, name, builtin, remark) VALUES
    ('security.password.min-length',    '8',  'INT',  '密码最少位数',           true, '少于 8 时仍按 8 执行'),
    ('security.password.min-classes',   '3',  'INT',  '密码字符类别下限',       true, '大写/小写/数字/符号四类中至少几类'),
    ('security.password.max-age-days',  '0',  'INT',  '密码有效天数',           true, '0 表示不过期'),
    ('security.lockout.max-failures',   '5',  'INT',  '连续失败锁定阈值',       true, '达到后锁定'),
    ('security.lockout.minutes',        '15', 'INT',  '锁定分钟数',             true, NULL),
    ('security.session.access-minutes', '30', 'INT',  '访问令牌分钟数',         true, NULL),
    ('security.session.refresh-days',   '7',  'INT',  '刷新令牌天数',           true, NULL),
    ('org.group.name',                  '集团', 'STRING', '集团名称',          true, '组织树根节点显示名');

-- ---------------------------------------------------------------------
-- 账号。口令即凭证，不另建 credentials 表（基础框架规格）。
-- ---------------------------------------------------------------------

CREATE TABLE sys_user (
    id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    username               varchar(64)  NOT NULL,
    person_id              uuid         NOT NULL REFERENCES org_person (id),
    password_hash          varchar(100) NOT NULL,
    status                 varchar(16)  NOT NULL DEFAULT 'ACTIVE'
                           CHECK (status IN ('ACTIVE', 'DISABLED')),
    super_admin            boolean      NOT NULL DEFAULT false,
    must_change_pwd        boolean      NOT NULL DEFAULT true,
    pwd_changed_at         timestamptz,
    fail_count             integer      NOT NULL DEFAULT 0 CHECK (fail_count >= 0),
    locked_until           timestamptz,
    default_plant_id       uuid         REFERENCES org_plant (id) ON DELETE SET NULL,
    last_plant_id          uuid         REFERENCES org_plant (id) ON DELETE SET NULL,
    last_login_at          timestamptz,
    last_login_ip          varchar(64),
    perm_version           integer      NOT NULL DEFAULT 0 CHECK (perm_version >= 0),
    -- 改密后作废更早签发的 access token（按 iat 的秒比较）。
    session_invalid_before timestamptz,
    lock_version           integer      NOT NULL DEFAULT 0,
    deleted                boolean      NOT NULL DEFAULT false,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    updated_by             uuid,
    CONSTRAINT ck_sys_user_username_lower CHECK (username = lower(username) AND char_length(username) >= 1)
);
CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username) WHERE NOT deleted;
CREATE UNIQUE INDEX uk_sys_user_person ON sys_user (person_id) WHERE NOT deleted;

-- 用户-工厂绑定 = 已有任职表。每人恰好一条 is_default = true（有任职时）。
ALTER TABLE org_person_plant
    ADD COLUMN is_default boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX uk_org_person_plant_default
    ON org_person_plant (person_id) WHERE is_default;

-- ---------------------------------------------------------------------
-- 刷新令牌：随机 256 位的 SHA-256 十六进制；同一 family_id 轮换，重放则整族作废。
-- plant_id 记住当前工厂，刷新后 access token 仍落在该厂（任职失效则重选）。
-- ---------------------------------------------------------------------

CREATE TABLE auth_refresh_token (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid         NOT NULL REFERENCES sys_user (id),
    token_hash   varchar(64)  NOT NULL,
    family_id    uuid         NOT NULL,
    plant_id     uuid         REFERENCES org_plant (id) ON DELETE SET NULL,
    expires_at   timestamptz  NOT NULL,
    revoked_at   timestamptz,
    ip           varchar(64),
    user_agent   varchar(512),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_auth_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_auth_refresh_token_family ON auth_refresh_token (family_id);
CREATE INDEX ix_auth_refresh_token_user ON auth_refresh_token (user_id);

-- 登出时作废的 access token jti，保留到原 exp。
CREATE TABLE auth_revoked_jti (
    jti         uuid         PRIMARY KEY,
    user_id     uuid         NOT NULL REFERENCES sys_user (id),
    expires_at  timestamptz  NOT NULL
);
CREATE INDEX ix_auth_revoked_jti_user ON auth_revoked_jti (user_id);

-- ---------------------------------------------------------------------
-- 有任职则恰好一个默认工厂；未删除的人员与未删除的账号恰好一对一。
-- 约束触发器推迟到事务提交，便于同一事务内先写人员再写账号、或整体替换任职。
-- ---------------------------------------------------------------------

CREATE FUNCTION mes_person_one_default_plant() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    pid  uuid;
    pids uuid[];
    n    int;
    d    int;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.person_id IS DISTINCT FROM OLD.person_id THEN
        pids := ARRAY[OLD.person_id, NEW.person_id];
    ELSE
        pids := ARRAY[COALESCE(NEW.person_id, OLD.person_id)];
    END IF;
    FOREACH pid IN ARRAY pids LOOP
        SELECT count(*), count(*) FILTER (WHERE is_default)
          INTO n, d
          FROM org_person_plant
         WHERE person_id = pid;
        IF n > 0 AND d <> 1 THEN
            RAISE EXCEPTION 'exactly one default plant required for person %', pid
                USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_org_person_plant_one_default
    AFTER INSERT OR UPDATE OR DELETE ON org_person_plant
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION mes_person_one_default_plant();

CREATE FUNCTION mes_person_one_user() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    pid  uuid;
    pids uuid[];
    pc   int;
    uc   int;
BEGIN
    IF TG_TABLE_NAME = 'org_person' THEN
        pids := ARRAY[COALESCE(NEW.id, OLD.id)];
    ELSIF TG_OP = 'UPDATE' AND NEW.person_id IS DISTINCT FROM OLD.person_id THEN
        pids := ARRAY[OLD.person_id, NEW.person_id];
    ELSE
        pids := ARRAY[COALESCE(NEW.person_id, OLD.person_id)];
    END IF;
    FOREACH pid IN ARRAY pids LOOP
        SELECT count(*) INTO pc FROM org_person WHERE id = pid AND NOT deleted;
        SELECT count(*) INTO uc FROM sys_user WHERE person_id = pid AND NOT deleted;
        IF pc <> uc THEN
            RAISE EXCEPTION 'person % must have exactly one account (persons=%, accounts=%)', pid, pc, uc
                USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_org_person_one_user
    AFTER INSERT OR UPDATE OR DELETE ON org_person
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION mes_person_one_user();

CREATE CONSTRAINT TRIGGER trg_sys_user_one_person
    AFTER INSERT OR UPDATE OR DELETE ON sys_user
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION mes_person_one_user();

-- 已有人员补账号：用户名 = 工号小写，必须改密。口令哈希对应一次随机口令，明文不落库，须管理员重置。
INSERT INTO sys_user (username, person_id, password_hash, status, must_change_pwd)
SELECT lower(p.code), p.id, '$2a$10$G1xONIoTlhVkAbQt1Vvjmu/q3Q8jGFYlBbLCkXSi0ckGMxDcyFfyC',
       CASE WHEN p.enabled THEN 'ACTIVE' ELSE 'DISABLED' END,
       true
  FROM org_person p
 WHERE NOT p.deleted
   AND NOT EXISTS (
        SELECT 1 FROM sys_user u WHERE u.person_id = p.id AND NOT u.deleted
   );

-- 种子超级管理员。初始口令 ChangeMe@1（满足策略），must_change_pwd = true。
-- 不预置工厂：没有任职时登录返回 403 NO_PLANT_ASSIGNMENT，避免无故造一个业务工厂。
INSERT INTO org_person (id, code, name, remark)
SELECT '00000000-0000-4000-8000-0000000000a1', 'ADMIN', '系统管理员', '种子超级管理员'
 WHERE NOT EXISTS (SELECT 1 FROM org_person WHERE code = 'ADMIN' AND NOT deleted);

INSERT INTO sys_user (
    id, username, person_id, password_hash, status, super_admin, must_change_pwd)
SELECT '00000000-0000-4000-8000-0000000000a2',
       'admin',
       '00000000-0000-4000-8000-0000000000a1',
       '$2a$10$ZF14XrdRGDiV0KdxqTpaqO/sOHzB3u7Y1VxBMi8A4fwEREdLrtxTa',
       'ACTIVE',
       true,
       true
 WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin' AND NOT deleted);
