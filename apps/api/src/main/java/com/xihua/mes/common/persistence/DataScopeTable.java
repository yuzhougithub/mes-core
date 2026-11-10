package com.xihua.mes.common.persistence;

import java.util.Locale;
import java.util.Map;

/**
 * Tables that participate in the second-layer data scope (docs/v1-foundation.md §4.3).
 * Master data is intentionally absent. {@code sys_oper_log} is registered for F5; the table is not
 * created yet. Its spec entry only names {@code user_id}, so department scopes match nothing there.
 */
public enum DataScopeTable {

    /** Direct {@code dept_id}; self column is {@code created_by}. */
    ORG_PERSON("org_person", DeptBinding.COLUMN, "dept_id", "created_by"),
    /** Department comes from {@code org_person.dept_id}. Self is the account itself ({@code id}). */
    SYS_USER("sys_user", DeptBinding.VIA_PERSON, null, "id"),
    /** Self column only. Department scopes do not apply. */
    SYS_OPER_LOG("sys_oper_log", DeptBinding.NONE, null, "user_id");

    public enum DeptBinding {
        COLUMN,
        VIA_PERSON,
        NONE
    }

    private static final Map<String, DataScopeTable> TABLES = Map.of(
            ORG_PERSON.table, ORG_PERSON,
            SYS_USER.table, SYS_USER,
            SYS_OPER_LOG.table, SYS_OPER_LOG);

    private final String table;
    private final DeptBinding deptBinding;
    private final String deptColumn;
    private final String selfColumn;

    DataScopeTable(String table, DeptBinding deptBinding, String deptColumn, String selfColumn) {
        this.table = table;
        this.deptBinding = deptBinding;
        this.deptColumn = deptColumn;
        this.selfColumn = selfColumn;
    }

    public DeptBinding deptBinding() {
        return deptBinding;
    }

    public String deptColumn() {
        return deptColumn;
    }

    public String selfColumn() {
        return selfColumn;
    }

    public static DataScopeTable ofTable(String tableName) {
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
