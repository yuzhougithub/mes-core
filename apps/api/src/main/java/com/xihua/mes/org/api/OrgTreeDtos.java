package com.xihua.mes.org.api;

import java.util.List;
import java.util.UUID;

public final class OrgTreeDtos {

    private OrgTreeDtos() {
    }

    /** {@code type} is {@code GROUP}, {@code PLANT}, {@code WORKSHOP}, or {@code LINE}. */
    public record OrgNode(String type, UUID id, String code, String name, List<OrgNode> children) {
    }
}
