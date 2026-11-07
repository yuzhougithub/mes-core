package com.xihua.mes.routing.api;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;
import com.xihua.mes.routing.api.OperationDtos.OperationCreate;
import com.xihua.mes.routing.api.OperationDtos.OperationView;
import com.xihua.mes.routing.app.OperationService;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationController {

    private final OperationService service;

    public OperationController(OperationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<OperationView> list(HttpServletRequest request, @RequestParam(required = false) String q) {
        return service.list(PageQuery.from(request), q);
    }

    @PostMapping
    public ResponseEntity<OperationView> create(@RequestBody OperationCreate req) {
        OperationView view = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/operations/" + view.id())).body(view);
    }

    @GetMapping("/{id}")
    public OperationView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
