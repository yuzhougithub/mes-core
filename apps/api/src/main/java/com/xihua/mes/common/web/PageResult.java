package com.xihua.mes.common.web;

import java.util.List;

/** List envelope: {@code { items, page, size, total }} (docs/v1-api.md). */
public record PageResult<T>(List<T> items, long page, long size, long total) {
}
