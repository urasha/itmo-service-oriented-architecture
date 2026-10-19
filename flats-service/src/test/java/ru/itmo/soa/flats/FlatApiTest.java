package ru.itmo.soa.flats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.api.*;
import ru.itmo.soa.flats.repository.InMemoryFlatRepository;
import ru.itmo.soa.flats.service.FlatService;
import java.time.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class FlatApiTest {
    static final String VALID = "{\"name\":\"Квартира на Невском\",\"coordinates\":{\"x\":30.31,\"y\":59.93},\"area\":54,\"price\":8500000.50,\"numberOfRooms\":2,\"new\":false,\"view\":\"YARD\",\"house\":{\"name\":\"Дом\",\"numberOfFloors\":5}}";
    final ObjectMapper mapper = JsonCodec.createMapper();
    MockMvc mvc;

    @BeforeEach void setup() throws ServletException {
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T22:00:00Z"), ZoneId.of("Europe/Moscow"));
        var decoder = new FlatInputDecoder(mapper);
        var service = new FlatService(new InMemoryFlatRepository(), decoder, clock);
        var filter = new RequestLimitsFilter(); filter.init(new MockFilterConfig());
        mvc = MockMvcBuilders.standaloneSetup(new FlatController(service, decoder), new StatisticsController(service, decoder))
                .setControllerAdvice(new ApiExceptionHandler(clock))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .addFilter(filter, "/*").build();
    }

    MockHttpServletResponse call(MockHttpServletRequestBuilder request, int status) throws Exception {
        var response = mvc.perform(request.secure(true)).andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
        return response;
    }
    JsonNode json(MockHttpServletResponse response) throws Exception { return mapper.readTree(response.getContentAsByteArray()); }
    long create(String input) throws Exception {
        var response = call(post("/flats").queryParam("flat", input), 201);
        assertEquals(0, response.getContentAsByteArray().length);
        return Long.parseLong(response.getHeader("Location").substring("/flats/".length()));
    }

    @Test void completeCrudAndDeepPatchPreserveGeneratedFields() throws Exception {
        long id = create(VALID);
        JsonNode before = json(call(get("/flats/" + id), 200));
        assertEquals("2026-10-13", before.get("creationDate").textValue());
        assertEquals("Квартира на Невском", before.get("name").textValue());
        assertTrue(before.get("transport").isNull());
        JsonNode patched = json(call(patch("/flats/" + id).queryParam("flat", "{\"coordinates\":{\"x\":40},\"house\":{\"year\":2000},\"transport\":\"NONE\",\"new\":true}"), 200));
        assertEquals(before.get("creationDate"), patched.get("creationDate"));
        assertEquals(before.get("coordinates").get("y"), patched.get("coordinates").get("y"));
        assertEquals(5, patched.get("house").get("numberOfFloors").intValue());
        assertTrue(patched.get("new").booleanValue());
        JsonNode replaced = json(call(put("/flats/" + id).queryParam("flat", VALID), 200));
        assertEquals(before, replaced);
        call(patch("/flats/" + id).queryParam("flat", "{\"area\":99,\"house\":{\"numberOfFloors\":0}}"), 422);
        assertEquals(before, json(call(get("/flats/" + id), 200)), "Failed PATCH must not write any changes");
        assertEquals(0, call(delete("/flats/" + id), 204).getContentAsByteArray().length);
        call(get("/flats/" + id), 404); call(delete("/flats/" + id), 404);
        assertTrue(create(VALID) > id, "Deleted IDs must not be reused");
    }

    @Test void emptyCollectionAndStatistics() throws Exception {
        var empty = call(get("/flats"), 200);
        assertEquals("0", empty.getHeader("X-Total-Count")); assertEquals(0, json(empty).size());
        call(get("/flats/min-creation-date"), 204); call(get("/flats/max-new"), 204);
        assertEquals(0, json(call(get("/flats/count-by-transport").queryParam("transport", "null"), 200)).get("count").longValue());
        long first = create(VALID);
        long second = create(VALID.replace("\"new\":false", "\"new\":true").replace("\"view\":\"YARD\"", "\"view\":\"GOOD\",\"transport\":\"NONE\""));
        assertEquals(first, json(call(get("/flats/min-creation-date"), 200)).get("id").longValue());
        assertEquals(second, json(call(get("/flats/max-new"), 200)).get("id").longValue());
        assertEquals(1, json(call(get("/flats/count-by-transport").queryParam("transport", "null"), 200)).get("count").longValue());
        assertEquals(1, json(call(get("/flats/count-by-transport").queryParam("transport", "\"NONE\""), 200)).get("count").longValue());
    }

    @Test void percentEncodedPlusAmpersandHashAndUnicodeAreDecodedExactlyOnce() throws Exception {
        String input = VALID.replace("Квартира на Невском", "Квартира + & # %22 😀");
        String encoded = java.net.URLEncoder.encode(input, java.nio.charset.StandardCharsets.UTF_8);
        var created = call(post(java.net.URI.create("/flats?flat=" + encoded)), 201);
        var flat = json(call(get(created.getHeader("Location")), 200));
        assertEquals("Квартира + & # %22 😀", flat.get("name").textValue());
    }

    @Test void selectionCombinesEveryFilterFieldAndPagedTotals() throws Exception {
        create(VALID); long target = create(VALID.replace("\"area\":54", "\"area\":55"));
        JsonNode flat = json(call(get("/flats/" + target), 200));
        var result = call(get("/flats").queryParam("filter", flat.toString()).queryParam("size", "1"), 200);
        assertEquals("1", result.getHeader("X-Total-Count")); assertEquals(target, json(result).get(0).get("id").longValue());
        var page = call(get("/flats").queryParam("sort", "area:desc", "name:asc").queryParam("size", "1"), 200);
        assertEquals("2", page.getHeader("X-Total-Count")); assertEquals(target, json(page).get(0).get("id").longValue());
        assertEquals(0, json(call(get("/flats").queryParam("page", "2147483647").queryParam("size", "2147483647"), 200)).size());
        assertEquals(2, json(call(get("/flats").queryParam("filter", "{\"house\":{}}"), 200)).size());
    }

    @ParameterizedTest @ValueSource(strings = {"page=0", "size=-1", "page=x", "size=2147483648", "page=1&page=2", "filter=%7B%7D&filter=%7B%7D", "sort=area:asc&sort=area:desc", "sort=bad:asc", "sort=name:ASC", "sort=name", "sort=", "filter=null", "filter=%7B%22area%22:0%7D", "unknown=1"})
    void invalidSelectionIs400(String query) throws Exception {
        error(call(get(java.net.URI.create("/flats?" + query)), 400));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808", "1.1"})
    void invalidIdIs400(String id) throws Exception { error(call(get("/flats/" + id), 400)); }

    @Test void invalidPayloadsAndBodyAreNotAcceptedAsQuery() throws Exception {
        call(post("/flats").contentType("application/json").content(VALID), 400);
        call(post("/flats").queryParam("flat", "{}"), 400);
        call(post("/flats").queryParam("flat", VALID, VALID), 400);
        call(post("/flats").queryParam("flat", VALID.replace("\"area\":54", "\"area\":\"54\"")), 400);
        error(call(post("/flats").queryParam("flat", VALID.replace("\"area\":54", "\"area\":0")), 422));
        call(post("/flats").queryParam("flat", VALID.replace("\"price\":8500000.50", "\"price\":1.001")), 422);
        long id = create(VALID);
        call(patch("/flats/" + id).queryParam("flat", "{}"), 400);
        call(patch("/flats/" + id).queryParam("flat", "{\"id\":123}"), 400);
        call(put("/flats/999").queryParam("flat", VALID), 404);
        call(get("/flats/count-by-transport").queryParam("transport", "NONE"), 400);
        call(get("/flats/count-by-transport").queryParam("transport", "\"BAD\""), 400);
        call(post("/flats/1"), 405);
    }

    @Test void largeNumbersRemainNumbersAndKeepPrecision() throws Exception {
        long id = create(VALID.replace("\"area\":54", "\"area\":9223372036854775807").replace("8500000.50", "9007199254740993.01"));
        JsonNode result = json(call(get("/flats/" + id), 200));
        assertEquals(Long.MAX_VALUE, result.get("area").longValue());
        assertEquals(0, new java.math.BigDecimal("9007199254740993.01").compareTo(result.get("price").decimalValue()));
    }

    @Test void uriLimitHttpsGuardAndReadCors() throws Exception {
        error(call(post("/flats").queryParam("flat", VALID + " ".repeat(17000)), 414));
        assertEquals(403, mvc.perform(get("/flats")).andReturn().getResponse().getStatus());
        assertEquals("*", call(get("/flats"), 200).getHeader("Access-Control-Allow-Origin"));
    }

    private void error(MockHttpServletResponse response) throws Exception {
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = json(response);
        assertEquals(Set.of("timestamp", "message"), mapper.convertValue(body, java.util.Map.class).keySet());
        assertTrue(body.get("timestamp").textValue().endsWith("Z"));
        assertFalse(body.get("message").textValue().isBlank());
    }
}
