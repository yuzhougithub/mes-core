package com.xihua.mes.config.domain;

import java.util.Arrays;
import java.util.Optional;

/** Entities that support custom fields (A6); wire name = {@code entity_type} column value. */
public enum CustomFieldEntityType {
    MATERIAL("material"),
    OPERATION("operation"),
    PERSON("person"),
    WORKSHOP("workshop"),
    WORK_CENTER("work_center"),
    EQUIPMENT("equipment");

    private final String wire;

    CustomFieldEntityType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Optional<CustomFieldEntityType> fromWire(String value) {
        return Arrays.stream(values()).filter(t -> t.wire.equals(value)).findFirst();
    }
}
