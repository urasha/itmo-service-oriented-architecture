package ru.itmo.soa.agency.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import ru.itmo.soa.agency.api.AgencyHttpFilter;
import ru.itmo.soa.agency.api.AgencyResource;
import ru.itmo.soa.agency.client.FlatsClient;
import ru.itmo.soa.agency.exception.AgencyErrorMapper;
import ru.itmo.soa.agency.service.AgencyService;
import ru.itmo.soa.contract.JsonCodec;

import java.util.Set;

@ApplicationPath("/agency")
public final class AgencyApplication extends Application {
    private final Set<Object> singletons;

    public AgencyApplication() {
        var settings = AgencySettings.load();
        var mapper = JsonCodec.createMapper();
        var client = new FlatsClient(settings, mapper);
        var errors = new AgencyErrorMapper(mapper);
        singletons =
                Set.of(
                        new AgencyResource(new AgencyService(client::get), settings, mapper),
                        errors,
                        new AgencyHttpFilter(settings.allowedOrigin(), errors));
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}
