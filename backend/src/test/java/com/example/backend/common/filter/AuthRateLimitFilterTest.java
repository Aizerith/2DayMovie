package com.example.backend.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.backend.common.config.AppProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    @Test
    void rejectsRequestsAfterConfiguredLimit() throws ServletException, IOException {
        AppProperties appProperties = new AppProperties();
        appProperties.getSecurity().getRateLimit().setMaxRequests(1);
        appProperties.getSecurity().getRateLimit().setWindowSeconds(60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(appProperties);

        MockHttpServletRequest firstRequest = postLoginRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, new MockFilterChain());

        MockHttpServletRequest secondRequest = postLoginRequest();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, new MockFilterChain());

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, secondResponse.getStatus());
        assertEquals("60", secondResponse.getHeader("Retry-After"));
    }

    private MockHttpServletRequest postLoginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
