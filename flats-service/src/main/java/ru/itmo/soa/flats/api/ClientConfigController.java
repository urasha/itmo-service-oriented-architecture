package ru.itmo.soa.flats.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.Map;

/** Public browser settings only: never expose the internal upstream URL or truststore. */
@RestController
public final class ClientConfigController {
    private final ObjectMapper mapper;
    private final String flats;
    private final String agency;
    private final int timeout;

    @Autowired
    public ClientConfigController(ObjectMapper mapper, Environment environment) {
        this(mapper, environment.getProperty("soa.flatsPublicBaseUrl", environment.getProperty("FLATS_PUBLIC_BASE_URL", "")),
                environment.getProperty("soa.agencyPublicBaseUrl", environment.getProperty("AGENCY_PUBLIC_BASE_URL", "")),
                Integer.parseInt(environment.getProperty("soa.clientTimeoutMillis", "20000")));
    }
    public ClientConfigController(ObjectMapper mapper, String flats, String agency, int timeout) {
        this.mapper = mapper; this.flats = validate(flats); this.agency = validate(agency); this.timeout = timeout;
        if (timeout < 1) throw new IllegalArgumentException("Client timeout must be positive");
    }
    private static String validate(String value) {
        if (value.isEmpty()) return value;
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Public client base URLs must be HTTPS without credentials, query or fragment");
        return value;
    }
    @GetMapping(value = "/app/config.js", produces = "application/javascript;charset=UTF-8")
    public ResponseEntity<String> config() throws JsonProcessingException {
        String script = "window.SOA_CONFIG = " + mapper.writeValueAsString(Map.of("flatsBaseUrl", flats,
                "agencyBaseUrl", agency, "requestTimeoutMillis", timeout)) + ";\n";
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(script);
    }
}
