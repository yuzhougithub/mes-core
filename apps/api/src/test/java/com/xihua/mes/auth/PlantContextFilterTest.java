package com.xihua.mes.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.xihua.mes.common.PlantNotSetException;

class PlantContextFilterTest {

    private final PlantContextFilter filter = new PlantContextFilter();

    @AfterEach
    void tearDown() {
        PlantContext.clear();
    }

    @Test
    void withHeaderSetsContextDuringChainAndClearsAfter() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(PlantContextFilter.HEADER_PLANT_ID)).thenReturn("plant-42");

        doAnswer(invocation -> {
            assertEquals("plant-42", PlantContext.require());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(PlantContext.get());
        assertThrows(PlantNotSetException.class, PlantContext::require);
    }

    @Test
    void withoutHeaderLeavesContextUnsetButPassesThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(PlantContextFilter.HEADER_PLANT_ID)).thenReturn(null);

        doAnswer(invocation -> {
            assertNull(PlantContext.get());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(PlantContext.get());
    }

    @Test
    void blankHeaderDoesNotSetContext() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(PlantContextFilter.HEADER_PLANT_ID)).thenReturn("  ");

        doAnswer(invocation -> {
            assertNull(PlantContext.get());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);
        assertNull(PlantContext.get());
    }

    @Test
    void clearsEvenWhenChainThrows() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(PlantContextFilter.HEADER_PLANT_ID)).thenReturn("plant-x");

        doAnswer(invocation -> {
            assertEquals("plant-x", PlantContext.get());
            throw new IllegalStateException("boom");
        }).when(chain).doFilter(any(), any());

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));
        assertNull(PlantContext.get());
    }
}
