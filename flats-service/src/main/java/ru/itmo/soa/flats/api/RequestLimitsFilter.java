package ru.itmo.soa.flats.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import ru.itmo.soa.contract.ApiError;
import ru.itmo.soa.contract.JsonCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class RequestLimitsFilter implements Filter {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            JsonCodec.createMapper();
    private int maxUriBytes;
    private String allowedOrigin;

    @Override
    public void init(FilterConfig config) {
        String value =
                System.getProperty(
                        "flats.maxUriBytes",
                        System.getenv().getOrDefault("FLATS_MAX_URI_BYTES", "16384"));
        maxUriBytes = Integer.parseInt(value);
        if (maxUriBytes < 1024) {
            throw new IllegalArgumentException("FLATS_MAX_URI_BYTES must be >= 1024");
        }
        allowedOrigin =
                System.getProperty(
                        "flats.allowedOrigin",
                        System.getenv().getOrDefault("FLATS_ALLOWED_ORIGIN", ""));
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        response.setCharacterEncoding("UTF-8");
        if (!request.isSecure()) {
            error(response, 403, "Доступ разрешён только по HTTPS");
            return;
        }
        if (handleCors(request, response)) {
            return;
        }
        if (uriTooLong(request)) {
            error(response, 414, "Слишком длинный URL");
            return;
        }
        chain.doFilter(req, res);
    }

    private boolean handleCors(HttpServletRequest request, HttpServletResponse response) {
        if (!request.getRequestURI().startsWith("/flats")) {
            return false;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "Origin");
        boolean allowedClient = isAllowedClient(request);
        allowResponseRead(request, response, allowedClient);
        if (allowedClient && request.getMethod().equals("OPTIONS")) {
            completePreflight(response);
            return true;
        }
        return false;
    }

    private boolean isAllowedClient(HttpServletRequest request) {
        return !allowedOrigin.isBlank() && allowedOrigin.equals(request.getHeader("Origin"));
    }

    private void allowResponseRead(
            HttpServletRequest request, HttpServletResponse response, boolean allowedClient) {
        if (allowedClient || request.getMethod().equals("GET")) {
            response.setHeader("Access-Control-Allow-Origin", allowedClient ? allowedOrigin : "*");
            response.setHeader("Access-Control-Expose-Headers", "X-Total-Count, Location");
        }
    }

    private void completePreflight(HttpServletResponse response) {
        response.setHeader(
                "Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Accept, Content-Type");
        response.setHeader("Access-Control-Max-Age", "600");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private boolean uriTooLong(HttpServletRequest request) {
        String query = request.getQueryString();
        String target = request.getRequestURI() + (query == null ? "" : "?" + query);
        return target.getBytes(StandardCharsets.UTF_8).length > maxUriBytes;
    }

    private void error(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        MAPPER.writeValue(response.getOutputStream(), new ApiError(Instant.now(), message));
    }
}
