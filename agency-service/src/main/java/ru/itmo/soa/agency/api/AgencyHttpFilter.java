package ru.itmo.soa.agency.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import lombok.RequiredArgsConstructor;

import ru.itmo.soa.agency.exception.AgencyErrorMapper;

@PreMatching
@RequiredArgsConstructor
public final class AgencyHttpFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private final String allowedOrigin;
    private final AgencyErrorMapper errors;

    @Override
    public void filter(ContainerRequestContext request) {
        if (!request.getSecurityContext().isSecure()) {
            request.abortWith(
                    Response.status(403)
                            .type(MediaType.APPLICATION_JSON)
                            .entity(errors.json(403, "Требуется HTTPS"))
                            .build());
        } else if ("OPTIONS".equals(request.getMethod())
                && request.getHeaderString("Access-Control-Request-Method") != null) {
            request.abortWith(Response.noContent().build());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle("Cache-Control", "no-store");
        response.getHeaders().add("Vary", "Origin");
        if (allowedOrigin.equals(request.getHeaderString("Origin"))) {
            response.getHeaders().putSingle("Access-Control-Allow-Origin", allowedOrigin);
            response.getHeaders().putSingle("Access-Control-Allow-Methods", "GET, OPTIONS");
            response.getHeaders().putSingle("Access-Control-Allow-Headers", "Accept, Content-Type");
            response.getHeaders().putSingle("Access-Control-Expose-Headers", "Location");
        }
    }
}
