-- =====================================================================
-- V5：菜单树成环的库层兜底。应用在写事务开头先取咨询锁，再读祖先链。
-- 触发器推迟到提交，用递归 CTE 拒绝环。
-- =====================================================================

CREATE FUNCTION mes_menu_no_cycle() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        WITH RECURSIVE ancestors AS (
            SELECT id, parent_id, ARRAY[id] AS path
              FROM sys_menu
             WHERE NOT deleted AND parent_id IS NOT NULL
            UNION ALL
            SELECT a.id, m.parent_id, a.path || m.id
              FROM ancestors a
              JOIN sys_menu m ON m.id = a.parent_id AND NOT m.deleted
             WHERE NOT m.id = ANY (a.path)
        )
        SELECT 1
          FROM ancestors a
         WHERE a.parent_id IS NOT NULL
           AND a.parent_id = ANY (a.path)
    ) THEN
        RAISE EXCEPTION 'sys_menu contains a cycle' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_sys_menu_no_cycle
    AFTER INSERT OR UPDATE OR DELETE ON sys_menu
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION mes_menu_no_cycle();
