package com.xihua.mes.system.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

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
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.system.api.RoleDtos.RoleCreate;
import com.xihua.mes.system.api.RoleDtos.RoleMenus;
import com.xihua.mes.system.api.RoleDtos.RoleUpdate;
import com.xihua.mes.system.api.RoleDtos.RoleView;
import com.xihua.mes.system.app.RoleService;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping
    @RequiresPerm("sys:role:list")
    public PageResult<RoleView> list(HttpServletRequest request, @RequestParam(required = false) String q) {
        return roles.list(PageQuery.from(request), q);
    }

    @PostMapping
    @RequiresPerm(value = "sys:role:create", global = true)
    public ResponseEntity<RoleView> create(@RequestBody(required = false) RoleCreate req) {
        RoleView created = roles.create(req);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @RequiresPerm("sys:role:view")
    public RoleView get(@PathVariable UUID id) {
        return roles.get(id);
    }

    @PutMapping("/{id}")
    @RequiresPerm(value = "sys:role:update", global = true)
    public RoleView update(@PathVariable UUID id, @RequestBody(required = false) RoleUpdate req) {
        return roles.update(id, req);
    }

    @DeleteMapping("/{id}")
    @RequiresPerm(value = "sys:role:delete", global = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        roles.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/menus")
    @RequiresPerm("sys:role:grant")
    public RoleMenus menus(@PathVariable UUID id) {
        return new RoleMenus(roles.menusOf(id));
    }

    @PutMapping("/{id}/menus")
    @RequiresPerm(value = "sys:role:grant", global = true)
    public RoleMenus replaceMenus(@PathVariable UUID id, @RequestBody(required = false) RoleMenus req) {
        return new RoleMenus(roles.replaceMenus(id, req));
    }
}
