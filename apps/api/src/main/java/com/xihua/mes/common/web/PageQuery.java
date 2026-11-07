package com.xihua.mes.common.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;

/**
 * {@code page} (1-based, default 1), {@code size} (default 20, max 200),
 * {@code sort=field,asc|desc} (repeatable, whitelist per resource, default {@code code,asc}).
 */
public record PageQuery(int page, int size, List<String> sort) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public static PageQuery of(Integer page, Integer size, List<String> sort) {
        List<FieldError> errors = new ArrayList<>();
        int p = page == null ? 1 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 1) {
            errors.add(new FieldError("page", ErrorCodes.OUT_OF_RANGE, "page 从 1 开始"));
        }
        if (s < 1 || s > MAX_SIZE) {
            errors.add(new FieldError("size", ErrorCodes.OUT_OF_RANGE, "size 须在 1–" + MAX_SIZE));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return new PageQuery(p, s, sort == null ? List.of() : sort);
    }

    /** Read {@code page}/{@code size}/{@code sort} from raw parameters (Spring would split {@code sort=code,asc}). */
    public static PageQuery from(jakarta.servlet.http.HttpServletRequest request) {
        String[] sorts = request.getParameterValues("sort");
        return of(intParam(request, "page"), intParam(request, "size"),
                sorts == null ? List.of() : List.of(sorts));
    }

    private static Integer intParam(jakarta.servlet.http.HttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.validation(List.of(new FieldError(name, ErrorCodes.INVALID_VALUE, name + " 须为整数")));
        }
    }

    /** {@code q}: ILIKE on code / name (escaped; parameterized). */
    public static <T> void fuzzy(QueryWrapper<T> qw, String q, String... columns) {
        if (q == null || q.isBlank()) {
            return;
        }
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        qw.and(w -> {
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) {
                    w.or();
                }
                w.apply(columns[i] + " ILIKE {0}", pattern);
            }
        });
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    /**
     * Apply whitelisted ORDER BY then LIMIT/OFFSET. {@code columns} maps API field → column.
     * Values are only ever whitelisted identifiers / integers, never raw client text.
     */
    public <T> void applyOrderAndLimit(QueryWrapper<T> qw, Map<String, String> columns, String defaultField) {
        List<String> specs = sort.isEmpty() ? List.of(defaultField + ",asc") : sort;
        List<FieldError> errors = new ArrayList<>();
        for (String spec : specs) {
            String[] parts = spec.split(",");
            String field = parts[0].trim();
            String dir = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
            String column = columns.get(field);
            if (column == null || !(dir.equals("asc") || dir.equals("desc")) || parts.length > 2) {
                errors.add(new FieldError("sort", ErrorCodes.INVALID_VALUE, "不支持的排序：" + spec));
                continue;
            }
            qw.orderBy(true, dir.equals("asc"), column);
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        qw.orderByAsc("id");
        qw.last("LIMIT " + size + " OFFSET " + offset());
    }
}
