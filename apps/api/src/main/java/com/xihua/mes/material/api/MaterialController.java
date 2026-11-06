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
import com.xihua.mes.material.api.MaterialDtos.LockRequest;
import com.xihua.mes.material.api.MaterialDtos.MaterialCreate;
import com.xihua.mes.material.api.MaterialDtos.MaterialUpdate;
import com.xihua.mes.material.api.MaterialDtos.MaterialView;
import com.xihua.mes.material.api.MaterialDtos.PlantExtPut;
import com.xihua.mes.material.api.MaterialDtos.PlantExtView;
import com.xihua.mes.material.app.MaterialService;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<MaterialView> list(HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String ids) {
        return service.list(PageQuery.from(request), q, status, materialType, categoryId, scope,
                MaterialService.parseIds(ids));
    }

    @PostMapping
    public ResponseEntity<MaterialView> create(@RequestBody MaterialCreate req) {
        MaterialView view = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/materials/" + view.id())).body(view);
    }

    @GetMapping("/{id}")
    public MaterialView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public MaterialView update(@PathVariable UUID id, @RequestBody MaterialUpdate req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        service.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/release")
    public MaterialView release(@PathVariable UUID id, @RequestBody LockRequest req) {
        return service.release(id, req.lockVersion());
    }

    @PostMapping("/{id}/obsolete")
    public MaterialView obsolete(@PathVariable UUID id, @RequestBody LockRequest req) {
        return service.obsolete(id, req.lockVersion());
    }

    @GetMapping("/{id}/plant-ext")
    public PlantExtView getPlantExt(@PathVariable UUID id) {
        return service.getPlantExt(id);
    }

    @PutMapping("/{id}/plant-ext")
    public PlantExtView putPlantExt(@PathVariable UUID id, @RequestBody PlantExtPut req) {
        return service.putPlantExt(id, req);
    }
}
