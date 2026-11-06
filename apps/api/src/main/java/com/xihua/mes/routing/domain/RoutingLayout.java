package com.xihua.mes.routing.domain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Canvas layout {@code rtg_routing.layout_json} (docs/architecture.md «布局 layout», A23).
 * <ul>
 *   <li>Explicit layout: strict whitelist — root {@code schemaVersion}(=1)/{@code nodes}; node {@code x}/{@code y}
 *       finite numbers; keys must be decimal step numbers of <em>this request's</em> steps. Unknown or orphan
 *       keys → 422 {@code LAYOUT_INVALID} (rejected, never trimmed). &gt; 64 KB → 422 {@code LAYOUT_TOO_LARGE}.</li>
 *   <li>Omitted layout: keep the stored layout but prune orphan keys against the new steps
 *       ({@link #pruneOrphans}), in the same transaction as the step replacement.</li>
 *   <li>Explicit {@code null}: clear.</li>
 * </ul>
 * Pure; not part of release business rules.
 */
public final class RoutingLayout {

    public static final int MAX_BYTES = 64 * 1024;
    public static final int SCHEMA_VERSION = 1;

    private static final Set<String> ROOT_KEYS = Set.of("schemaVersion", "nodes");
    private static final Set<String> NODE_KEYS = Set.of("x", "y");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RoutingLayout() {
    }

    /**
     * Validate an explicitly submitted, non-null layout against the request's step numbers.
     *
     * @return the layout to store (a defensive copy)
     */
    public static ObjectNode validateExplicit(JsonNode layout, Set<Integer> stepNos) {
        int bytes = MAPPER.writeValueAsString(layout).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) {
            throw ApiException.unprocessable(ErrorCodes.LAYOUT_TOO_LARGE, "layout 序列化后超过 64 KB",
                    List.of(new FieldError("layout", ErrorCodes.LAYOUT_TOO_LARGE, "layout 不能超过 64 KB")));
        }
        List<FieldError> errors = new ArrayList<>();
        if (!layout.isObject()) {
            throw invalid(List.of(new FieldError("layout", ErrorCodes.LAYOUT_INVALID, "layout 必须是对象")));
        }
        for (String key : layout.propertyNames()) {
            if (!ROOT_KEYS.contains(key)) {
                errors.add(new FieldError("layout." + key, ErrorCodes.LAYOUT_INVALID, "未知键：" + key));
            }
        }
        JsonNode version = layout.get("schemaVersion");
        if (version == null || !version.isIntegralNumber() || version.intValue() != SCHEMA_VERSION) {
            errors.add(new FieldError("layout.schemaVersion", ErrorCodes.LAYOUT_INVALID, "schemaVersion 必须为 1"));
        }
        JsonNode nodes = layout.get("nodes");
        if (nodes == null || !nodes.isObject()) {
            errors.add(new FieldError("layout.nodes", ErrorCodes.LAYOUT_INVALID, "nodes 必须是对象"));
        } else {
            for (String key : nodes.propertyNames()) {
                String path = "layout.nodes." + key;
                Integer stepNo = parseStepKey(key);
                if (stepNo == null) {
                    errors.add(new FieldError(path, ErrorCodes.LAYOUT_INVALID, "节点键须为正整数 stepNo：" + key));
                } else if (!stepNos.contains(stepNo)) {
                    errors.add(new FieldError(path, ErrorCodes.LAYOUT_INVALID, "不属于本次提交的工序节点：" + key));
                }
                JsonNode node = nodes.get(key);
                if (!node.isObject()) {
                    errors.add(new FieldError(path, ErrorCodes.LAYOUT_INVALID, "节点坐标须为 {x, y}"));
                    continue;
                }
                for (String nk : node.propertyNames()) {
                    if (!NODE_KEYS.contains(nk)) {
                        errors.add(new FieldError(path + "." + nk, ErrorCodes.LAYOUT_INVALID, "未知键：" + nk));
                    }
                }
                for (String axis : List.of("x", "y")) {
                    JsonNode v = node.get(axis);
                    if (v == null || !v.isNumber() || !Double.isFinite(v.doubleValue())) {
                        errors.add(new FieldError(path + "." + axis, ErrorCodes.LAYOUT_INVALID,
                                axis + " 须为有限数字"));
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        return (ObjectNode) layout.deepCopy();
    }

    /**
     * Omitted-layout path: drop stored {@code nodes} keys whose step no longer exists.
     *
     * @return pruned copy, or {@code null} when nothing is stored
     */
    public static JsonNode pruneOrphans(JsonNode stored, Set<Integer> stepNos) {
        if (stored == null || stored.isNull() || !stored.isObject()) {
            return null;
        }
        ObjectNode copy = (ObjectNode) stored.deepCopy();
        JsonNode nodes = copy.get("nodes");
        if (nodes == null || !nodes.isObject()) {
            return copy;
        }
        ObjectNode kept = JsonNodeFactory.instance.objectNode();
        for (String key : nodes.propertyNames()) {
            Integer stepNo = parseStepKey(key);
            if (stepNo != null && stepNos.contains(stepNo)) {
                kept.set(key, nodes.get(key));
            }
        }
        copy.set("nodes", kept);
        return copy;
    }

    /** Canonical positive decimal ({@code "10"}; not {@code "010"}, {@code "+10"}, {@code "1e1"}). */
    static Integer parseStepKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 9 || key.charAt(0) == '0') {
            return null;
        }
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) < '0' || key.charAt(i) > '9') {
                return null;
            }
        }
        return Integer.parseInt(key);
    }

    private static ApiException invalid(List<FieldError> errors) {
        return ApiException.unprocessable(ErrorCodes.LAYOUT_INVALID, "layout 不合法", errors);
    }
}
