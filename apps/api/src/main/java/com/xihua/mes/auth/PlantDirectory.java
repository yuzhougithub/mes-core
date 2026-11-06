package com.xihua.mes.auth;

import java.util.UUID;

/**
 * Read-only plant lookup used by {@link PlantAccessInterceptor}. Implemented by the {@code org} package
 * (auth → org read-only dependency, docs/v1-master-data-overview.md «模块边界»).
 */
public interface PlantDirectory {

    /** @return true if the plant exists, is not deleted and is enabled */
    boolean isActive(UUID plantId);
}
