package com.xihua.mes.org.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.auth.RequiresPerm;
import com.xihua.mes.org.api.DeptDtos.DeptMove;
import com.xihua.mes.org.api.DeptDtos.DeptNode;
import com.xihua.mes.org.api.DeptDtos.DeptWrite;
import com.xihua.mes.org.app.DeptService;

@RestController
@RequestMapping("/api/v1/depts")
public class DeptController {

    private final DeptService depts;

    public DeptController(DeptService depts) {
        this.depts = depts;
    }

    @GetMapping("/tree")
    @RequiresPerm("org:dept:list")
    public List<DeptNode> tree() {
        return depts.tree();
    }

    @PostMapping
    @RequiresPerm("org:dept:create")
    public ResponseEntity<DeptNode> create(@RequestBody(required = false) DeptWrite req) {
        DeptNode created = depts.create(req);
        return ResponseEntity.created(URI.create("/api/v1/depts/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequiresPerm("org:dept:update")
    public DeptNode update(@PathVariable UUID id, @RequestBody(required = false) DeptWrite req) {
        return depts.update(id, req);
    }

    @PostMapping("/{id}/move")
    @RequiresPerm("org:dept:update")
    public DeptNode move(@PathVariable UUID id, @RequestBody(required = false) DeptMove req) {
        return depts.move(id, req);
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("org:dept:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        depts.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }
}
