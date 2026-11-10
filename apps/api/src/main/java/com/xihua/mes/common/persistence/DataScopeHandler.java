package com.xihua.mes.common.persistence;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;

/**
 * Builds the §4.3 predicate for one table. Inactive unless the mapped statement or the calling
 * service method is annotated with {@link DataScope}. {@code ALL} and {@code PLANT} add nothing.
 * Other roles are OR-ed. No matching role matches nothing.
 */
public final class DataScopeHandler implements MultiDataPermissionHandler {

    private final ConcurrentMap<String, Lookup> annotations = new ConcurrentHashMap<>();

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (table == null) {
            return null;
        }
        DataScopeTable registered = DataScopeTable.ofTable(table.getName());
        if (registered == null) {
            return null;
        }
        Lookup lookup = annotations.computeIfAbsent(
                mappedStatementId == null ? "" : mappedStatementId, DataScopeHandler::lookup);
        boolean active = lookup.scope() || DataScopeHolder.scopeActive();
        if (!active) {
            return null;
        }
        String alias = lookup.alias() != null ? lookup.alias() : DataScopeHolder.alias();
        if (alias != null && !alias.equalsIgnoreCase(aliasOf(table))) {
            return null;
        }
        DataScopeDecision decision = DataScopeBridge.current();
        if (decision.wideOpen()) {
            return null;
        }
        return parse(predicate(table, registered, decision));
    }

    private static String predicate(Table table, DataScopeTable registered, DataScopeDecision decision) {
        List<String> parts = new ArrayList<>();
        if (decision.has(DataScopeDecision.DEPT_AND_CHILD)) {
            parts.add(deptPredicate(table, registered, deptAndChild(decision)));
        }
        if (decision.has(DataScopeDecision.DEPT)) {
            parts.add(deptPredicate(table, registered, deptEquals(decision)));
        }
        if (decision.has(DataScopeDecision.CUSTOM)) {
            parts.add(deptPredicate(table, registered, customIn(decision)));
        }
        if (decision.has(DataScopeDecision.SELF)) {
            parts.add(selfPredicate(table, registered, decision));
        }
        if (parts.isEmpty()) {
            return "1 = 0";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return "(" + String.join(" OR ", parts) + ")";
    }

    /**
     * {@code onDeptColumn} is a boolean expression written against an unqualified {@code dept_id}.
     * Person rows use that column directly. Accounts correlate through {@code org_person}.
     */
    private static String deptPredicate(Table table, DataScopeTable registered, String onDeptColumn) {
        return switch (registered.deptBinding()) {
            case COLUMN -> onDeptColumn.replace("dept_id", qualify(table, registered.deptColumn()));
            case VIA_PERSON -> "1 = 0".equals(onDeptColumn)
                    ? "1 = 0"
                    : qualify(table, "person_id")
                            + " IN (SELECT id FROM org_person WHERE NOT deleted AND " + onDeptColumn + ")";
            case NONE -> "1 = 0";
        };
    }

    private static String deptEquals(DataScopeDecision decision) {
        if (decision.deptId() == null) {
            return "1 = 0";
        }
        return "dept_id = '" + decision.deptId() + "'";
    }

    private static String deptAndChild(DataScopeDecision decision) {
        if (decision.ancestors() == null || decision.ancestors().isBlank()) {
            return "1 = 0";
        }
        return "dept_id IN (SELECT id FROM org_dept WHERE NOT deleted AND ancestors LIKE '"
                + escapeLike(decision.ancestors()) + "%')";
    }

    private static String customIn(DataScopeDecision decision) {
        if (decision.customDeptIds() == null || decision.customDeptIds().isEmpty()) {
            return "1 = 0";
        }
        StringBuilder sql = new StringBuilder("dept_id IN (");
        boolean first = true;
        for (UUID id : decision.customDeptIds()) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append('\'').append(id).append('\'');
        }
        return sql.append(')').toString();
    }

    private static String selfPredicate(Table table, DataScopeTable registered, DataScopeDecision decision) {
        if (decision.userId() == null || registered.selfColumn() == null) {
            return "1 = 0";
        }
        return qualify(table, registered.selfColumn()) + " = '" + decision.userId() + "'";
    }

    private static String qualify(Table table, String column) {
        String alias = aliasOf(table);
        if (alias == null) {
            return column;
        }
        return alias + "." + column;
    }

    private static String aliasOf(Table table) {
        if (table.getAlias() == null || table.getAlias().getName() == null || table.getAlias().getName().isBlank()) {
            return null;
        }
        return table.getAlias().getName();
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("'", "''").replace("%", "\\%").replace("_", "\\_");
    }

    private static Expression parse(String sql) {
        try {
            return CCJSqlParserUtil.parseCondExpression(sql);
        } catch (JSQLParserException ex) {
            throw new IllegalStateException("data scope predicate: " + sql, ex);
        }
    }

    private record Lookup(boolean scope, String alias) {
    }

    private static Lookup lookup(String mappedStatementId) {
        int dot = mappedStatementId.lastIndexOf('.');
        if (dot <= 0 || dot == mappedStatementId.length() - 1) {
            return new Lookup(false, null);
        }
        String className = mappedStatementId.substring(0, dot);
        String methodName = mappedStatementId.substring(dot + 1);
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return new Lookup(false, null);
        }
        DataScope found = null;
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                found = method.getAnnotation(DataScope.class);
                break;
            }
        }
        if (found == null) {
            found = type.getAnnotation(DataScope.class);
        }
        if (found == null) {
            return new Lookup(false, null);
        }
        String alias = found.alias() == null || found.alias().isBlank() ? null : found.alias();
        return new Lookup(true, alias);
    }
}
