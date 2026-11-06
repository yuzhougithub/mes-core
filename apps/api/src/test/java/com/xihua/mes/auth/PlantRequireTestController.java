package com.xihua.mes.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used by {@link PlantContextMvcTest}.
 * Uses {@link CurrentContext} as the business entry (PR#5).
 */
@RestController
@RequestMapping("/__test/plant")
class PlantRequireTestController {

    @GetMapping
    Map<String, String> currentPlant() {
        return Map.of("plantId", CurrentContext.requirePlantId());
    }
}
