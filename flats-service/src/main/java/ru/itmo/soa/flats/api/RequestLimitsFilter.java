package ru.itmo.soa.flats.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import ru.itmo.soa.contract.ApiError;
import ru.itmo.soa.contract.JsonCodec;

/** Defense in depth; the supplied Jetty base has no cleartext connector. */
public final class RequestLimitsFilter implements Filter {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = JsonCodec.createMapper();
    private int maxUriBytes;
    @Override
    public void init(FilterConfig config) {
        String value = System.getProperty("flats.maxUriBytes", System.getenv().getOrDefault("FLATS_MAX_URI_BYTES", "16384"));
        maxUriBytes = Integer.parseInt(value);
        if (maxUriBytes < 1024) throw new IllegalArgumentException("FLATS_MAX_URI_BYTES must be >= 1024");
    }
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        response.setCharacterEncoding("UTF-8");
        if (request.getRequestURI().startsWith("/flats")) {
            response.setHeader("Cache-Control", "no-store");
            if (request.getMethod().equals("GET")) {
                response.setHeader("Access-Control-Allow-Origin", "*");
                response.setHeader("Access-Control-Expose-Headers", "X-Total-Count, Location");
            }
        }
        if (!request.isSecure()) { error(response, 403, "Доступ разрешён только по HTTPS"); return; }
        String target = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        if (target.getBytes(StandardCharsets.UTF_8).length > maxUriBytes) {
            error(response, 414, "Слишком длинный URL"); return;
        }
        chain.doFilter(req, res);
    }
    private void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        MAPPER.writeValue(response.getOutputStream(), new ApiError(Instant.now(), message));
    }
}
