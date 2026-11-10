package com.xihua.mes.common.persistence;

import java.util.ArrayDeque;
import java.util.Deque;

/** Stack of {@link DataScope} / {@link CrossPlant} frames for the current thread. */
public final class DataScopeHolder {

    private record Frame(String alias, boolean scope, boolean cross) {
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
        stack.push(new Frame(alias, scope, cross));
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
