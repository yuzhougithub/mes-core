package com.xihua.mes.system.api;

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
import com.xihua.mes.system.api.MenuDtos.MenuNode;
import com.xihua.mes.system.api.MenuDtos.MenuWrite;
import com.xihua.mes.system.app.MenuService;

@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menus;

    public MenuController(MenuService menus) {
        this.menus = menus;
    }

    @GetMapping("/tree")
    @RequiresPerm("sys:menu:list")
    public List<MenuNode> tree() {
        return menus.tree();
    }

    @GetMapping("/{id}")
    @RequiresPerm("sys:menu:view")
    public MenuNode get(@PathVariable UUID id) {
        return menus.get(id);
    }

    @PostMapping
    @RequiresPerm("sys:menu:create")
    public ResponseEntity<MenuNode> create(@RequestBody(required = false) MenuWrite req) {
        MenuNode created = menus.create(req);
        return ResponseEntity.created(URI.create("/api/v1/menus/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequiresPerm("sys:menu:update")
    public MenuNode update(@PathVariable UUID id, @RequestBody(required = false) MenuWrite req) {
        return menus.update(id, req);
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("sys:menu:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        menus.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }
}
