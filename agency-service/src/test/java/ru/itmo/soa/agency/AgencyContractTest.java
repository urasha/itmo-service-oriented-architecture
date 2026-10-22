package ru.itmo.soa.agency;

import static org.junit.jupiter.api.Assertions.*;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.junit.jupiter.api.Test;

import ru.itmo.soa.agency.api.AgencyResource;
import ru.itmo.soa.agency.config.AgencyApplication;

import java.util.*;

class AgencyContractTest {
    @Test
    void publicMappingsMatchBothOpenApiOperations() {
        var options = new ParseOptions();
        options.setResolve(true);
        var parsed = new OpenAPIV3Parser().readLocation("../public/agency.yaml", null, options);
        assertNotNull(parsed.getOpenAPI(), () -> String.valueOf(parsed.getMessages()));
        assertTrue(
                parsed.getMessages() == null || parsed.getMessages().isEmpty(),
                () -> String.valueOf(parsed.getMessages()));
        Set<String> actual = new HashSet<>();
        String prefix = AgencyApplication.class.getAnnotation(ApplicationPath.class).value();
        for (var method : AgencyResource.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GET.class)) {
                actual.add(prefix + "/" + method.getAnnotation(Path.class).value());
            }
        }
        assertEquals(parsed.getOpenAPI().getPaths().keySet(), actual);
        for (var path : parsed.getOpenAPI().getPaths().values()) {
            assertEquals(
                    Set.of("200", "303", "400", "404", "502", "504"),
                    path.getGet().getResponses().keySet());
        }
    }
}
