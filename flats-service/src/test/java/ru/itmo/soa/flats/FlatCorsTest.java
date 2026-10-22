package ru.itmo.soa.flats;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;

import ru.itmo.soa.flats.api.RequestLimitsFilter;

class FlatCorsTest {
    private static final String ORIGIN = "https://se.ifmo.ru";
    private String previousOrigin;
    private RequestLimitsFilter filter;

    @BeforeEach
    void setup() {
        previousOrigin = System.getProperty("flats.allowedOrigin");
        System.setProperty("flats.allowedOrigin", ORIGIN);
        filter = new RequestLimitsFilter();
        filter.init(new MockFilterConfig());
    }

    @AfterEach
    void restore() {
        if (previousOrigin == null) {
            System.clearProperty("flats.allowedOrigin");
        } else {
            System.setProperty("flats.allowedOrigin", previousOrigin);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void staticClientCanReadResponsesForEveryOperation(String method) throws Exception {
        var response = call(method, ORIGIN, true, new MockFilterChain());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Expose-Headers").contains("Location"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflightAllowsClientMethodsWithoutReachingControllers() throws Exception {
        var chain = new MockFilterChain();
        var response = call("OPTIONS", ORIGIN, true, chain);
        assertEquals(204, response.getStatus());
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PATCH"));
        assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("Content-Type"));
        assertNull(chain.getRequest());
    }

    @Test
    void anotherOriginDoesNotReceiveWriteCorsPermission() throws Exception {
        assertNull(
                call("POST", "https://other.example", true, new MockFilterChain())
                        .getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightCannotBypassHttpsGuard() throws Exception {
        var chain = new MockFilterChain();
        assertEquals(403, call("OPTIONS", ORIGIN, false, chain).getStatus());
        assertNull(chain.getRequest());
    }

    private MockHttpServletResponse call(
            String method, String origin, boolean secure, MockFilterChain chain) throws Exception {
        var request = new MockHttpServletRequest(method, "/flats");
        request.setSecure(secure);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "PATCH");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
