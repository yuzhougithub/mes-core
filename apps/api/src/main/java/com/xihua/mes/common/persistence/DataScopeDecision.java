package com.xihua.mes.common.persistence;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Effective data scope for the current user in the current plant.
 * Several roles are unioned: {@code ALL} or {@code PLANT} wins; otherwise each role is OR-ed.
 */
public record DataScopeDecision(
        boolean unrestricted,
        boolean all,
        Set<String> scopes,
        UUID deptId,
        String ancestors,
        UUID userId,
        List<UUID> customDeptIds,
        List<UUID> assignedPlantIds) {

    public static final String ALL = "ALL";
    public static final String PLANT = "PLANT";
    public static final String DEPT_AND_CHILD = "DEPT_AND_CHILD";
    public static final String DEPT = "DEPT";
    public static final String SELF = "SELF";
    public static final String CUSTOM = "CUSTOM";

    /** Sentinel while the decision itself is being loaded. Not cached. */
    static DataScopeDecision loading() {
        return new DataScopeDecision(false, false, Set.of(), null, null, null, List.of(), List.of());
    }

    /** No resolver yet (unit tests of the plant filter). Second layer stays off; plant filter stays. */
    public static DataScopeDecision ordinary() {
        return loading();
    }

    public boolean wideOpen() {
        return unrestricted || all || scopes.contains(PLANT);
    }

    public boolean has(String scope) {
        return scopes.contains(scope);
    }
}
