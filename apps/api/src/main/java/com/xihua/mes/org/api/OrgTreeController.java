package com.xihua.mes.org.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.org.api.OrgTreeDtos.OrgNode;
import com.xihua.mes.org.app.OrgTreeService;

/** Login is enough. The projection is group → plant → workshop → line. */
@RestController
@RequestMapping("/api/v1/org")
public class OrgTreeController {

    private final OrgTreeService trees;

    public OrgTreeController(OrgTreeService trees) {
        this.trees = trees;
    }

    @GetMapping("/tree")
    public OrgNode tree() {
        return trees.tree();
    }
}
