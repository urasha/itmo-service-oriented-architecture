package ru.itmo.soa.agency;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.itmo.soa.agency.config.AgencySettings;
import ru.itmo.soa.agency.exception.AgencyException;
import ru.itmo.soa.agency.service.AgencyService;
import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.JsonCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class AgencyServiceTest {
    static final String FLAT =
            """
            {"id":1,"name":"Flat + &","coordinates":{"x":1,"y":2},"creationDate":"2026-10-12",
             "area":30,"price":100.01,"numberOfRooms":2,"new":false,"view":"GOOD","transport":null,
             "house":{"name":"","numberOfFloors":2}}
            """;

    private Flat flat(long id, String price) {
        try {
            return JsonCodec.createMapper()
                    .readValue(
                            FLAT.replace("\"id\":1", "\"id\":" + id).replace("100.01", price),
                            Flat.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void comparesExactDecimalPricesAndKeepsFirstTie() {
        var data =
                List.of(
                        flat(1, "9007199254740992.01"),
                        flat(2, "9007199254740992.02"),
                        flat(3, "9007199254740992.020"));
        var calls = new ArrayList<Long>();
        var service =
                new AgencyService(
                        id -> {
                            calls.add(id);
                            return data.get((int) id - 1);
                        });
        assertEquals(1, service.select(true, 2, 1).id());
        assertEquals(2, service.select(false, 2, 3, 1).id());
        assertEquals(3, service.select(false, 3, 2, 1).id());
        assertEquals(List.of(2L, 1L, 2L, 3L, 1L, 3L, 2L, 1L), calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "1.0", "abc", "9223372036854775808", " ", "1e2"})
    void rejectsBadIdsAs400(String value) {
        assertEquals(
                400, assertThrows(AgencyException.class, () -> AgencyService.id(value)).status());
    }

    @Test
    void acceptsInt64Boundary() {
        assertEquals(Long.MAX_VALUE, AgencyService.id("9223372036854775807"));
        assertEquals(1, AgencyService.id("001"));
    }

    @Test
    void preservesUpstreamFailure() {
        var service =
                new AgencyService(
                        id -> {
                            throw new AgencyException(404, "missing");
                        });
        assertEquals(
                404,
                assertThrows(AgencyException.class, () -> service.select(true, 1, 2)).status());
    }

    @Test
    void configResolvesPathsAndSeparatePublicBase(@TempDir Path directory) throws Exception {
        var file = directory.resolve("agency.properties");
        Files.writeString(
                file,
                "flats.internalBaseUrl=https://internal.example:9443/context/\n"
                        + "flats.publicBaseUrl=https://public.example/\n"
                        + "flats.trustStore=tls/store.p12\n"
                        + "flats.trustStorePasswordFile=tls/store.password\n"
                        + "cors.allowedOrigin=https://client.example\n");
        var settings = AgencySettings.load(file);
        assertEquals(directory.resolve("tls/store.p12"), settings.trustStore());
        assertEquals(
                "https://internal.example:9443/context/flats/12",
                AgencySettings.flatUri(settings.internalBaseUrl(), 12).toString());
        assertEquals(
                "https://public.example/flats/12",
                AgencySettings.flatUri(settings.publicBaseUrl(), 12).toString());
        Files.writeString(
                file, Files.readString(file).replace("https://internal", "http://internal"));
        assertThrows(IllegalArgumentException.class, () -> AgencySettings.load(file));
    }
}
