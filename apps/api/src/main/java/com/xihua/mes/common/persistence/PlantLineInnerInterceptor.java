package com.xihua.mes.common.persistence;

import java.util.List;
import java.util.UUID;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.SelectItem;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.xihua.mes.auth.CurrentContext;

/**
 * MyBatis-Plus row filter by {@code plant_id} from {@link CurrentContext}.
 * <p>
 * SELECT / UPDATE / DELETE on tables registered in {@link PlantScope} get
 * {@code plant_id = :p} (plant-owned / ext) or {@code (plant_id IS NULL OR plant_id = :p)} (shared).
 * INSERT is never rewritten: services set {@code plant_id} explicitly (shared rows keep it null).
 * Fails closed: touching a scoped table without a plant context throws {@code PLANT_REQUIRED}.
 * Cross-plant maintenance queries opt out with {@code @InterceptorIgnore(tenantLine = "true")}.
 * {@link CrossPlant} plus data scope {@code ALL} replaces the current-plant predicate with the
 * caller's active assignments. A super admin (or header mode) with {@link CrossPlant} skips it.
 */
public class PlantLineInnerInterceptor extends TenantLineInnerInterceptor {

    public static final String COLUMN = "plant_id";

    public PlantLineInnerInterceptor() {
        super(new Handler());
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        PlantScope scope = PlantScope.ofTable(table.getName());
        if (scope == null) {
            return null;
        }
        if (DataScopeHolder.crossPlant()) {
            DataScopeDecision decision = DataScopeBridge.current();
            if (decision.unrestricted()) {
                return null;
            }
            if (decision.all()) {
                return assignedPlants(table, scope, decision.assignedPlantIds());
            }
        }
        Column column = getAliasColumn(table);
        Expression plant = currentPlantLiteral();
        EqualsTo equals = new EqualsTo(column, plant);
        if (scope == PlantScope.PLANT) {
            return equals;
        }
        IsNullExpression isNull = new IsNullExpression();
        isNull.setLeftExpression(getAliasColumn(table));
        return new ParenthesedExpressionList<>(new OrExpression(isNull, equals));
    }

    private Expression assignedPlants(Table table, PlantScope scope, List<UUID> plantIds) {
        Expression in = inPlants(getAliasColumn(table), plantIds);
        if (scope == PlantScope.PLANT) {
            return in;
        }
        IsNullExpression isNull = new IsNullExpression();
        isNull.setLeftExpression(getAliasColumn(table));
        return new ParenthesedExpressionList<>(new OrExpression(isNull, in));
    }

    private static Expression inPlants(Column column, List<UUID> plantIds) {
        if (plantIds == null || plantIds.isEmpty()) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }
        ParenthesedExpressionList<Expression> list = new ParenthesedExpressionList<>();
        for (UUID id : plantIds) {
            list.add(new StringValue(id.toString()));
        }
        InExpression in = new InExpression();
        in.setLeftExpression(column);
        in.setRightExpression(list);
        return in;
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        // never inject plant_id on insert
    }

    @Override
    protected void appendSelectItem(List<SelectItem<?>> selectItems) {
        // never inject plant_id on INSERT ... SELECT
    }

    static Expression currentPlantLiteral() {
        // Parsed as UUID first: guarantees a safe literal (no quoting issues).
        UUID plant = CurrentContext.requirePlantUuid();
        return new StringValue(plant.toString());
    }

    private static final class Handler implements TenantLineHandler {
        @Override
        public Expression getTenantId() {
            return currentPlantLiteral();
        }

        @Override
        public String getTenantIdColumn() {
            return COLUMN;
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return PlantScope.ofTable(tableName) == null;
        }

        @Override
        public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
            return true;
        }
    }
}
