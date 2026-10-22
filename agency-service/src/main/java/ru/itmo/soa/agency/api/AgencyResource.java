package ru.itmo.soa.agency.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import lombok.RequiredArgsConstructor;

import ru.itmo.soa.agency.config.AgencySettings;
import ru.itmo.soa.agency.service.AgencyService;

import java.util.Arrays;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public final class AgencyResource {
    private final AgencyService service;
    private final AgencySettings settings;
    private final ObjectMapper mapper;

    @GET
    @Path("get-cheapest/{id1}/{id2}")
    public Response cheapest(@PathParam("id1") String id1, @PathParam("id2") String id2)
            throws JsonProcessingException {
        return compare(true, AgencyService.id(id1), AgencyService.id(id2));
    }

    @GET
    @Path("get-most-expensive/{id1}/{id2}/{id3}")
    public Response mostExpensive(
            @PathParam("id1") String id1,
            @PathParam("id2") String id2,
            @PathParam("id3") String id3)
            throws JsonProcessingException {
        return compare(false, AgencyService.id(id1), AgencyService.id(id2), AgencyService.id(id3));
    }

    private Response compare(boolean cheapest, long... ids) throws JsonProcessingException {
        if (Arrays.stream(ids).allMatch(id -> id == ids[0])) {
            return Response.seeOther(AgencySettings.flatUri(settings.publicBaseUrl(), ids[0]))
                    .build();
        }
        return Response.ok(
                        mapper.writeValueAsString(service.select(cheapest, ids)),
                        MediaType.APPLICATION_JSON)
                .build();
    }
}
