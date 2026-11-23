package ru.itmo.soa.agency.client;

import com.ecwid.consul.ConsulException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import lombok.RequiredArgsConstructor;
import ru.itmo.soa.agency.exception.AgencyException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;

@RequiredArgsConstructor
public final class FlatsDiscovery {
    private final ConsulClient consul;

    public URI endpoint() {
        try {
            var instances = consul.getHealthServices("flats", true, QueryParams.DEFAULT).getValue();
            if (instances.isEmpty()) {
                throw AgencyException.upstream();
            }
            var service = instances.get(0).getService();
            return new URI("https", null, service.getAddress(), service.getPort(), null, null, null);
        } catch (ConsulException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw AgencyException.timeout(e);
            }
            throw AgencyException.upstream(e);
        } catch (URISyntaxException e) {
            throw AgencyException.upstream(e);
        }
    }
}
