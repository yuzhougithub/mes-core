package com.xihua.mes.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Vector;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.xihua.mes.common.PlantHeaderInvalidException;
import com.xihua.mes.common.PlantNotSetException;

class PlantContextFilterTest {

    private final PlantContextFilter filter = new PlantContextFilter("header");

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    @Test
    void withHeaderSetsContextDuringChainAndClearsAfter() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        stubHeaders(request, PlantContextFilter.HEADER_PLANT_ID, "plant-42");
        stubHeaders(request, PlantContextFilter.HEADER_USER_ID, "user-9");

        doAnswer(invocation -> {
            assertEquals("plant-42", PlantContext.require());
            assertEquals("user-9", CurrentContext.userId());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(PlantContext.get());
        assertNull(CurrentContext.userId());
        assertThrows(PlantNotSetException.class, PlantContext::require);
    }

    @Test
    void withoutHeaderLeavesContextUnsetButPassesThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        stubHeaders(request, PlantContextFilter.HEADER_PLANT_ID);
        stubHeaders(request, PlantContextFilter.HEADER_USER_ID);

        doAnswer(invocation -> {
            assertNull(PlantContext.get());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(PlantContext.get());
    }

    @Test
    void blankHeaderReturns400PlantHeaderInvalid() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        stubHeaders(request, PlantContextFilter.HEADER_PLANT_ID, "  ");
        stubHeaders(request, PlantContextFilter.HEADER_USER_ID);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(response.isCommitted()).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertTrue(body.toString().contains(PlantHeaderInvalidException.CODE));
        assertNull(PlantContext.get());
    }

    @Test
    void multiValueHeaderReturns400PlantHeaderInvalid() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        stubHeaders(request, PlantContextFilter.HEADER_PLANT_ID, "a", "b");
        stubHeaders(request, PlantContextFilter.HEADER_USER_ID);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(response.isCommitted()).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertTrue(body.toString().contains(PlantHeaderInvalidException.CODE));
    }

    @Test
    void jwtModeIgnoresPlantHeader() throws Exception {
        PlantContextFilter jwtFilter = new PlantContextFilter("jwt");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(PlantContextFilter.HEADER_PLANT_ID)).thenReturn("plant-ignored");
        stubHeaders(request, PlantContextFilter.HEADER_PLANT_ID, "plant-ignored");
        stubHeaders(request, PlantContextFilter.HEADER_USER_ID);

        doAnswer(invocation -> {
            assertNull(PlantContext.get());
            return null;
        }).when(chain).doFilter(any(), any());

        jwtFilter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertNull(PlantContext.get());
    }

    @Test
    void clearsEvenWhenChainThrows() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        stubHeaders(request, PlantContextFilter.HEADER_PLANT_ID, "plant-x");
        stubHeaders(request, PlantContextFilter.HEADER_USER_ID);

        doAnswer(invocation -> {
            assertEquals("plant-x", PlantContext.get());
            throw new IllegalStateException("boom");
        }).when(chain).doFilter(any(), any());

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));
        assertNull(PlantContext.get());
    }

    private static void stubHeaders(HttpServletRequest request, String name, String... values) {
        Vector<String> vector = new Vector<>(List.of(values));
        when(request.getHeaders(name)).thenReturn(vector.elements());
        when(request.getHeader(name)).thenReturn(values.length == 0 ? null : values[0]);
        when(request.getHeaders(name)).thenAnswer(inv -> new Vector<>(List.of(values)).elements());
    }
}
