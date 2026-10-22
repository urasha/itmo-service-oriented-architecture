package ru.itmo.soa.agency.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import ru.itmo.soa.contract.ApiError;

import java.time.Instant;
import java.util.logging.Level;

@Log
@RequiredArgsConstructor
public final class AgencyErrorMapper implements ExceptionMapper<Throwable> {
    private final ObjectMapper mapper;

    @Override
    public Response toResponse(Throwable error) {
        if (error instanceof AgencyException agency) {
            return agencyError(agency);
        }
        if (error instanceof WebApplicationException web) {
            return httpError(web.getResponse());
        }
        log.log(Level.SEVERE, "Unhandled agency request error", error);
        return errorResponse(Response.serverError(), 500, "Внутренняя ошибка сервиса агентства");
    }

    private Response agencyError(AgencyException error) {
        if (error.getCause() != null) {
            log.log(Level.FINE, "Upstream request failed", error.getCause());
        }
        return errorResponse(Response.status(error.status()), error.status(), error.getMessage());
    }

    private Response httpError(Response original) {
        int status = original.getStatus();
        return errorResponse(Response.fromResponse(original), status, httpMessage(status));
    }

    private String httpMessage(int status) {
        return switch (status) {
            case 400 -> "Некорректный запрос";
            case 404 -> "Ресурс не найден";
            case 405 -> "Метод не поддерживается";
            case 406 -> "Требуется Accept: application/json";
            default -> "Ошибка HTTP " + status;
        };
    }

    private Response errorResponse(Response.ResponseBuilder builder, int status, String message) {
        return builder.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(json(status, message))
                .build();
    }

    public String json(int status, String message) {
        try {
            return mapper.writeValueAsString(new ApiError(Instant.now(), message));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize API error " + status, e);
        }
    }
}
