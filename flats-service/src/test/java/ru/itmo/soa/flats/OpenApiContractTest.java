package ru.itmo.soa.flats;

import static org.junit.jupiter.api.Assertions.*;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

class OpenApiContractTest {
    @Test
    void resolvesBothSpecificationsAndImplementsEveryFirstServiceOperation() throws Exception {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var parser = new OpenAPIV3Parser();
        var first =
                parser.readLocation(
                        Path.of("../public/flats.yaml").toAbsolutePath().toString(), null, options);
        var second =
                parser.readLocation(
                        Path.of("../public/agency.yaml").toAbsolutePath().toString(),
                        null,
                        options);
        assertNotNull(first.getOpenAPI(), first.getMessages().toString());
        assertNotNull(second.getOpenAPI(), second.getMessages().toString());
        assertTrue(first.getMessages().isEmpty(), first.getMessages().toString());
        assertTrue(second.getMessages().isEmpty(), second.getMessages().toString());
        first.getOpenAPI()
                .getComponents()
                .getSchemas()
                .forEach(
                        (name, schema) -> {
                            var common = second.getOpenAPI().getComponents().getSchemas().get(name);
                            if (common != null) {
                                assertEquals(schema, common, "Shared schema differs: " + name);
                            }
                        });
        Set<String> expected = new HashSet<>();
        first.getOpenAPI()
                .getPaths()
                .forEach(
                        (path, item) ->
                                item.readOperationsMap()
                                        .forEach(
                                                (method, operation) ->
                                                        expected.add(method.name() + " " + path)));
        assertEquals(9, expected.size());
        FlatApiTest api = new FlatApiTest();
        api.setup();
        var mappings =
                api.mvc
                        .getDispatcherServlet()
                        .getWebApplicationContext()
                        .getBean(RequestMappingHandlerMapping.class);
        Set<String> actual = new HashSet<>();
        mappings.getHandlerMethods()
                .forEach(
                        (mapping, handler) -> {
                            for (String path : mapping.getPatternValues()) {
                                for (RequestMethod method :
                                        mapping.getMethodsCondition().getMethods()) {
                                    actual.add(method.name() + " " + path);
                                }
                            }
                        });
        assertEquals(expected, actual);
        String sample =
                ru.itmo.soa.contract.JsonCodec.createMapper()
                        .writeValueAsString(
                                first.getOpenAPI()
                                        .getComponents()
                                        .getParameters()
                                        .get("FlatInput")
                                        .getContent()
                                        .get("application/json")
                                        .getExample());
        long id = api.create(sample);
        var flat =
                api.json(
                        api.call(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/flats/" + id),
                                200));
        io.swagger.v3.oas.models.media.Schema<?> schema =
                first.getOpenAPI().getComponents().getSchemas().get("Flat");
        assertTrue(flat.has("new"));
        assertEquals(
                schema.getProperties().keySet(),
                api.mapper.convertValue(flat, java.util.Map.class).keySet());
        for (String name : schema.getRequired()) {
            assertTrue(flat.has(name), name);
        }
    }
}
