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
import ru.itmo.soa.contract.Flat;

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
        long firstId = AgencyIdParser.parse(id1);
        long secondId = AgencyIdParser.parse(id2);
        if (firstId == secondId) {
            return redirect(firstId);
        }
        return flatResponse(service.getCheapest(firstId, secondId));
    }

    @GET
    @Path("get-most-expensive/{id1}/{id2}/{id3}")
    public Response mostExpensive(
            @PathParam("id1") String id1,
            @PathParam("id2") String id2,
            @PathParam("id3") String id3)
            throws JsonProcessingException {
        long firstId = AgencyIdParser.parse(id1);
        long secondId = AgencyIdParser.parse(id2);
        long thirdId = AgencyIdParser.parse(id3);
        if (firstId == secondId && firstId == thirdId) {
            return redirect(firstId);
        }
        return flatResponse(service.getMostExpensive(firstId, secondId, thirdId));
    }

    private Response redirect(long id) {
        return Response.seeOther(AgencySettings.flatUri(settings.publicBaseUrl(), id)).build();
    }

    private Response flatResponse(Flat flat) throws JsonProcessingException {
        return Response.ok(mapper.writeValueAsString(flat), MediaType.APPLICATION_JSON).build();
    }
}
