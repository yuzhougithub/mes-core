package com.xihua.mes.config.form;

/**
 * Optional layout hints only (order / group / span 1–24). Not business meaning.
 */
public record FieldLayout(Integer order, String group, Integer span) {
}
