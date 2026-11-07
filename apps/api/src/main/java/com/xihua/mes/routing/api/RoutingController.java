package com.xihua.mes.routing.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.routing.api.RoutingDtos.RoutingView;
import com.xihua.mes.routing.api.RoutingDtos.RoutingWrite;
import com.xihua.mes.routing.app.RoutingService;

/** Minimal routing draft save (slice 1). List / release / obsolete / copy / effective are deferred. */
@RestController
@RequestMapping("/api/v1/routings")
public class RoutingController {

    private final RoutingService service;

    public RoutingController(RoutingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RoutingView> create(@RequestBody RoutingWrite req) {
        RoutingView view = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/routings/" + view.id())).body(view);
    }

    @GetMapping("/{id}")
    public RoutingView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public RoutingView update(@PathVariable UUID id, @RequestBody RoutingWrite req) {
        return service.update(id, req);
    }
}
