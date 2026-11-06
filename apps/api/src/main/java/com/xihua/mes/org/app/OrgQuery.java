package com.xihua.mes.org.app;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xihua.mes.org.infra.OrgLookupMapper;

/** Public read-only org lookups for other packages. */
@Service
public class OrgQuery {

    private final OrgLookupMapper lookups;

    public OrgQuery(OrgLookupMapper lookups) {
        this.lookups = lookups;
    }

    /** @return true if the work center exists in the current plant */
    public boolean workCenterVisible(UUID id) {
        return id != null && lookups.workCenterVisible(id);
    }
}
