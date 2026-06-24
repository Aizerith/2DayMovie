package com.example.backend.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    @Test
    void reusesIncomingRequestId() throws ServletException, IOException {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "request-123");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("request-123", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }

    @Test
    void generatesRequestIdWhenMissing() throws ServletException, IOException {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }
}
