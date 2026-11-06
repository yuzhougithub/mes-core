package com.xihua.mes.material.domain;

/** Shared lifecycle of material / BOM / routing: DRAFT → RELEASED → OBSOLETE. */
public enum VersionStatus {
    DRAFT,
    RELEASED,
    OBSOLETE
}
