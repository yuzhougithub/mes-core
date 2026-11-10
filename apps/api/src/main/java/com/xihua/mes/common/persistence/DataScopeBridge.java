package com.xihua.mes.common.persistence;

/**
 * Static entry so MyBatis interceptors (created beside the Spring context) can read the
 * request's data-scope decision. The resolver binds itself at startup.
 */
public final class DataScopeBridge {

    private static final ThreadLocal<DataScopeDecision> OVERRIDE = new ThreadLocal<>();
    private static volatile DataScopeResolver resolver;

    private DataScopeBridge() {
    }

    public static void bind(DataScopeResolver bound) {
        resolver = bound;
    }

    /** Unit tests inject a decision without a Spring resolver. */
    public static void override(DataScopeDecision decision) {
        if (decision == null) {
            OVERRIDE.remove();
        } else {
            OVERRIDE.set(decision);
        }
    }

    public static DataScopeDecision current() {
        DataScopeDecision forced = OVERRIDE.get();
        if (forced != null) {
            return forced;
        }
        DataScopeResolver current = resolver;
        if (current == null) {
            return DataScopeDecision.ordinary();
        }
        return current.current();
    }

    public static void clear() {
        OVERRIDE.remove();
        DataScopeResolver current = resolver;
        if (current != null) {
            current.clear();
        }
    }
}
