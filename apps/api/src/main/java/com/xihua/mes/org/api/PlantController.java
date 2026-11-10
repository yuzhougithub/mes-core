package com.xihua.mes.org.api;

import java.net.URI;
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
import com.xihua.mes.org.api.PlantDtos.PlantCreate;
import com.xihua.mes.org.api.PlantDtos.PlantUpdate;
import com.xihua.mes.org.api.PlantDtos.PlantView;
import com.xihua.mes.org.app.PlantService;

/** {@code /api/v1/plants}: global, not filtered by current plant (no X-Plant-Id needed). */
@RestController
@RequestMapping("/api/v1/plants")
public class PlantController {

    private final PlantService service;

    public PlantController(PlantService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPerm("org:plant:list")
    public PageResult<PlantView> list(HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled) {
        return service.list(PageQuery.from(request), q, enabled);
    }

    @PostMapping
    @RequiresPerm(value = "org:plant:create", global = true)
    public ResponseEntity<PlantView> create(@RequestBody PlantCreate req) {
        PlantView view = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/plants/" + view.id())).body(view);
    }

    @GetMapping("/{id}")
    @RequiresPerm("org:plant:view")
    public PlantView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @RequiresPerm(value = "org:plant:update", global = true)
    public PlantView update(@PathVariable UUID id, @RequestBody PlantUpdate req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @RequiresPerm(value = "org:plant:delete", global = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        service.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }
}
