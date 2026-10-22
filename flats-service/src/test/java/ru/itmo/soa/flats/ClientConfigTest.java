package ru.itmo.soa.flats;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ru.itmo.soa.contract.JsonCodec;
import ru.itmo.soa.flats.api.ClientConfigController;

class ClientConfigTest {
    @Test
    void servesOnlyPublicBrowserUrlsAsJavascriptWithoutCaching() throws Exception {
        var mapper = JsonCodec.createMapper();
        var controller =
                new ClientConfigController(
                        mapper,
                        "https://public.example:9443",
                        "https://agency.example:9444",
                        25000);
        var converters =
                new java.util.ArrayList<
                        org.springframework.http.converter.HttpMessageConverter<?>>();
        new ru.itmo.soa.flats.config.WebConfig().configureMessageConverters(converters);
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setMessageConverters(
                                converters.toArray(
                                        org.springframework.http.converter.HttpMessageConverter[]
                                                ::new))
                        .build();
        var response = mvc.perform(get("/app/config.js").secure(true)).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/javascript"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        String body = response.getContentAsString();
        var config =
                mapper.readTree(
                        body.substring("window.SOA_CONFIG = ".length(), body.lastIndexOf(';')));
        assertEquals(3, config.size());
        assertEquals("https://agency.example:9444", config.get("agencyBaseUrl").textValue());
        assertEquals(25000, config.get("requestTimeoutMillis").intValue());
    }

    @Test
    void rejectsUnsafePublicUrlsAndTimeout() {
        var mapper = JsonCodec.createMapper();
        for (String url :
                new String[] {
                    "http://example.org",
                    "https://u:p@example.org",
                    "https://example.org?q=x",
                    "https://example.org#fragment"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ClientConfigController(mapper, "", url, 20000));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClientConfigController(mapper, "", "", 0));
    }
}
