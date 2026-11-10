package com.xihua.mes.common.persistence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/** Stack of {@link DataScope} / {@link CrossPlant} frames for the current thread. */
public final class DataScopeHolder {

    private record Frame(String alias, boolean scope, boolean cross, boolean suppress) {
    }

    private static final ThreadLocal<Deque<Frame>> STACK = new ThreadLocal<>();

    private DataScopeHolder() {
    }

    public static void push(String alias, boolean scope, boolean cross) {
        Deque<Frame> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        stack.push(new Frame(alias, scope, cross, false));
    }

    /**
     * The user aggregate loads and updates {@code org_person} after {@code sys_user} was already
     * filtered. That follow-up must not apply a second self column ({@code created_by}).
     */
    public static <T> T supplyUnscoped(Supplier<T> supplier) {
        Deque<Frame> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        stack.push(new Frame(null, false, false, true));
        try {
            return supplier.get();
        } finally {
            pop();
        }
    }

    public static boolean suppressed() {
        Deque<Frame> stack = STACK.get();
        if (stack == null) {
            return false;
        }
        for (Frame frame : stack) {
            if (frame.suppress()) {
                return true;
            }
        }
        return false;
    }

    public static void pop() {
        Deque<Frame> stack = STACK.get();
        if (stack == null) {
            return;
        }
        stack.poll();
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static void clear() {
        STACK.remove();
    }

    public static boolean scopeActive() {
        Deque<Frame> stack = STACK.get();
        if (stack == null) {
            return false;
        }
        for (Frame frame : stack) {
            if (frame.scope) {
                return true;
            }
        }
        return false;
    }

    public static boolean crossPlant() {
        Deque<Frame> stack = STACK.get();
        if (stack == null) {
            return false;
        }
        for (Frame frame : stack) {
            if (frame.cross) {
                return true;
            }
        }
        return false;
    }

    /** Alias from the nearest scoped frame, or null when every scoped frame left it blank. */
    public static String alias() {
        Deque<Frame> stack = STACK.get();
        if (stack == null) {
            return null;
        }
        for (Frame frame : stack) {
            if (frame.scope && frame.alias != null && !frame.alias.isBlank()) {
                return frame.alias;
            }
        }
        return null;
    }
}
