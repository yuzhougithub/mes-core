package com.xihua.mes.config.form;

import java.util.List;
import java.util.Map;

/**
 * One dynamic-form field. JSON keys match architecture metadata
 * ({@code key}, {@code type}, {@code title}, {@code required}, {@code options},
 * {@code validation}, {@code layout}). No {@code widget} / Formily {@code x-*} keys.
 */
public record FieldDefinition(
        String key,
        FieldType type,
        String title,
        Boolean required,
        List<FieldOption> options,
        Map<String, Object> validation,
        FieldLayout layout) {
}
