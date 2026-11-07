package com.xihua.mes.config.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.config.api.CustomFieldSchemaDtos.SchemaPut;
import com.xihua.mes.config.api.CustomFieldSchemaDtos.SchemaView;
import com.xihua.mes.config.app.CustomFieldSchemaService;

/** {@code /api/v1/custom-field-schemas/{entityType}} — current plant's configuration. */
@RestController
@RequestMapping("/api/v1/custom-field-schemas")
public class CustomFieldSchemaController {

    private final CustomFieldSchemaService service;

    public CustomFieldSchemaController(CustomFieldSchemaService service) {
        this.service = service;
    }

    @GetMapping("/{entityType}")
    public SchemaView get(@PathVariable String entityType) {
        return service.get(CustomFieldSchemaService.parseEntityType(entityType));
    }

    @PutMapping("/{entityType}")
    public SchemaView put(@PathVariable String entityType, @RequestBody SchemaPut body) {
        return service.put(CustomFieldSchemaService.parseEntityType(entityType), body.schema(), body.lockVersion());
    }
}
