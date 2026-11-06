package com.xihua.mes.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used by {@link PlantContextMvcTest}.
 */
@RestController
@RequestMapping("/__test/plant")
class PlantRequireTestController {

    @GetMapping
    Map<String, String> currentPlant() {
        return Map.of("plantId", PlantContext.require());
    }
}
