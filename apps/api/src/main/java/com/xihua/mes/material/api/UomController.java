package com.xihua.mes.material.api;

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

import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.material.api.UomDtos.UomCreate;
import com.xihua.mes.material.api.UomDtos.UomUpdate;
import com.xihua.mes.material.api.UomDtos.UomView;
import com.xihua.mes.material.app.UomService;

@RestController
@RequestMapping("/api/v1/uoms")
public class UomController {

    private final UomService service;

    public UomController(UomService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<UomView> list(HttpServletRequest request, @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled) {
        return service.list(PageQuery.from(request), q, enabled);
    }

    @PostMapping
    public ResponseEntity<UomView> create(@RequestBody UomCreate req) {
        UomView view = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/uoms/" + view.id())).body(view);
    }

    @GetMapping("/{id}")
    public UomView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public UomView update(@PathVariable UUID id, @RequestBody UomUpdate req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        service.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }
}
