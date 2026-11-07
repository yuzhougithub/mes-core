package com.xihua.mes.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.routing.domain.RoutingLayout;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RoutingLayoutTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode j(String s) {
        return JSON.readTree(s);
    }

    @Test
    void acceptsPartialLayoutForRequestSteps() {
        JsonNode out = RoutingLayout.validateExplicit(
                j("{\"schemaVersion\":1,\"nodes\":{\"10\":{\"x\":120,\"y\":80},\"110\":{\"x\":1.5,\"y\":-3}}}"),
                Set.of(10, 20, 110));
        assertEquals(2, out.get("nodes").size());
    }

    @Test
    void rejectsOrphanKeyInsteadOfTrimming() {
        ApiException ex = assertThrows(ApiException.class, () -> RoutingLayout.validateExplicit(
                j("{\"schemaVersion\":1,\"nodes\":{\"10\":{\"x\":1,\"y\":1},\"30\":{\"x\":2,\"y\":2}}}"),
                Set.of(10, 20)));
        assertEquals("LAYOUT_INVALID", ex.getCode());
        assertThat(ex.getErrors()).extracting(e -> e.field()).containsExactly("layout.nodes.30");
    }

    @Test
    void rejectsUnknownKeysAndBadValues() {
        assertEquals("LAYOUT_INVALID", assertThrows(ApiException.class, () -> RoutingLayout.validateExplicit(
                j("{\"schemaVersion\":1,\"nodes\":{},\"zoom\":2}"), Set.of())).getCode());
        assertEquals("LAYOUT_INVALID", assertThrows(ApiException.class, () -> RoutingLayout.validateExplicit(
                j("{\"schemaVersion\":1,\"nodes\":{\"10\":{\"x\":1,\"y\":1,\"label\":\"a\"}}}"),
                Set.of(10))).getCode());
        assertEquals("LAYOUT_INVALID", assertThrows(ApiException.class, () -> RoutingLayout.validateExplicit(
                j("{\"schemaVersion\":1,\"nodes\":{\"10\":{\"x\":\"1\",\"y\":1}}}"), Set.of(10))).getCode());
        assertEquals("LAYOUT_INVALID", assertThrows(ApiException.class, () -> RoutingLayout.validateExplicit(
                j("{\"schemaVersion\":2,\"nodes\":{}}"), Set.of())).getCode());
        assertEquals("LAYOUT_INVALID", assertThrows(ApiException.class, () -> RoutingLayout.validateExplicit(
                j("{\"schemaVersion\":1,\"nodes\":{\"010\":{\"x\":1,\"y\":1}}}"), Set.of(10))).getCode());
        assertEquals("LAYOUT_INVALID", assertThrows(ApiException.class, () -> RoutingLayout.validateExplicit(
                j("[1,2]"), Set.of())).getCode());
    }

    @Test
    void rejectsTooLarge() {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"nodes\":{");
        for (int i = 1; i <= 3000; i++) {
            sb.append(i > 1 ? "," : "").append('"').append(i).append("\":{\"x\":123456.789,\"y\":987654.321}");
        }
        sb.append("}}");
        ApiException ex = assertThrows(ApiException.class,
                () -> RoutingLayout.validateExplicit(j(sb.toString()), Set.of()));
        assertEquals("LAYOUT_TOO_LARGE", ex.getCode());
    }

    @Test
    void pruneDropsOrphansAndKeepsTheRest() {
        JsonNode pruned = RoutingLayout.pruneOrphans(
                j("{\"schemaVersion\":1,\"nodes\":{\"10\":{\"x\":1,\"y\":1},\"20\":{\"x\":2,\"y\":2},"
                        + "\"30\":{\"x\":3,\"y\":3}}}"),
                Set.of(10, 20));
        assertThat(pruned.get("nodes").propertyNames()).containsExactlyInAnyOrder("10", "20");
        assertEquals(1, pruned.get("schemaVersion").intValue());
        assertNull(RoutingLayout.pruneOrphans(null, Set.of(10)));
    }
}
