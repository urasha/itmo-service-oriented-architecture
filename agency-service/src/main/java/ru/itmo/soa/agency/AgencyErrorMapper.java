package ru.itmo.soa.agency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import ru.itmo.soa.contract.ApiError;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AgencyErrorMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(AgencyErrorMapper.class.getName());
    private final ObjectMapper mapper;
    public AgencyErrorMapper(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public Response toResponse(Throwable error) {
        int status = 500;
        String message = "Внутренняя ошибка сервиса агентства";
        Response.ResponseBuilder builder = Response.serverError();
        if (error instanceof AgencyException agency) {
            status = agency.status(); message = agency.getMessage();
            if (agency.getCause() != null) LOG.log(Level.FINE, "Upstream request failed", agency.getCause());
        }
        else if (error instanceof WebApplicationException web) {
            status = web.getResponse().getStatus();
            message = switch (status) {
                case 400 -> "Некорректный запрос";
                case 404 -> "Ресурс не найден";
                case 405 -> "Метод не поддерживается";
                case 406 -> "Требуется Accept: application/json";
                default -> "Ошибка HTTP " + status;
            };
            builder = Response.fromResponse(web.getResponse());
        } else LOG.log(Level.SEVERE, "Unhandled agency request error", error);
        return builder.status(status).type(MediaType.APPLICATION_JSON).entity(json(status, message)).build();
    }
    public String json(int status, String message) {
        try { return mapper.writeValueAsString(new ApiError(Instant.now(), message)); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize API error " + status, e); }
    }
}
