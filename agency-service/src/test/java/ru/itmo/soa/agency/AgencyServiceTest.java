package ru.itmo.soa.agency;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ru.itmo.soa.agency.exception.AgencyException;
import ru.itmo.soa.agency.service.AgencyService;
import ru.itmo.soa.agency.service.FlatSource;
import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.JsonCodec;

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
        var source = new RecordingFlatSource(data);
        var service = new AgencyService(source);
        assertEquals(1, service.getCheapest(2, 1).id());
        assertEquals(2, service.getMostExpensive(2, 3, 1).id());
        assertEquals(3, service.getMostExpensive(3, 2, 1).id());
        assertEquals(List.of(2L, 1L, 2L, 3L, 1L, 3L, 2L, 1L), source.calls);
    }

    @Test
    void preservesUpstreamFailure() {
        var service =
                new AgencyService(
                        new FlatSource() {
                            @Override
                            public Flat get(long id) {
                                throw new AgencyException(404, "missing");
                            }
                        });
        assertEquals(
                404, assertThrows(AgencyException.class, () -> service.getCheapest(1, 2)).status());
    }

    private static final class RecordingFlatSource implements FlatSource {
        private final List<Flat> flats;
        private final List<Long> calls = new ArrayList<>();

        private RecordingFlatSource(List<Flat> flats) {
            this.flats = flats;
        }

        @Override
        public Flat get(long id) {
            calls.add(id);
            return flats.get((int) id - 1);
        }
    }
}
