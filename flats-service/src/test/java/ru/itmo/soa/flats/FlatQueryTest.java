package ru.itmo.soa.flats;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.service.FlatQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

class FlatQueryTest {
    @Test
    void signedZeroUsesNumericEqualityAndIdTieBreaker() {
        Flat first = withX(1, 0.0f);
        Flat second = withX(2, -0.0f);
        assertEquals(List.of(first, second), select(List.of(second, first), "coordinates.x:asc"));
        assertEquals(List.of(first, second), select(List.of(second, first), "coordinates.x:desc"));
        var filter = JsonCodec.parse(JsonCodec.createMapper(), "{\"coordinates\":{\"x\":0}}");
        assertEquals(
                List.of(first, second),
                FlatQuery.of(1, 20, filter, List.of()).select(List.of(second, first)).items());
    }

    private Flat withX(long id, float x) {
        return new Flat(
                id,
                "A",
                new Coordinates(x, 1),
                LocalDate.of(2026, 1, 1),
                10,
                BigDecimal.ONE,
                1,
                false,
                View.YARD,
                null,
                new House("A", null, 1, null, null));
    }

    private static Flat flat(long id, String name, Long year, Transport transport, View view) {
        return new Flat(
                id,
                name,
                new Coordinates(id, id * 2),
                LocalDate.of(2020 + (int) id, 1, 1),
                10 * id,
                BigDecimal.valueOf(id),
                (int) id,
                id % 2 == 0,
                view,
                transport,
                new House(name, year, (int) id, (int) id, (int) id));
    }

    static Stream<String> leafFields() {
        return Stream.of(
                "id",
                "name",
                "coordinates.x",
                "coordinates.y",
                "creationDate",
                "area",
                "price",
                "numberOfRooms",
                "new",
                "view",
                "transport",
                "house.name",
                "house.year",
                "house.numberOfFloors",
                "house.numberOfFlatsOnFloor",
                "house.numberOfLifts");
    }

    private List<Flat> select(List<Flat> flats, String... sorts) {
        return FlatQuery.of(1, 20, null, List.of(sorts)).select(flats).items();
    }

    @ParameterizedTest
    @MethodSource("leafFields")
    void everyLeafPathSupportsSortingAndFiltering(String path) {
        Flat first = flat(1, "A", 1990L, Transport.FEW, View.YARD);
        Flat second = flat(2, "B", 2000L, Transport.NONE, View.NORMAL);
        assertEquals(List.of(first, second), select(List.of(second, first), path + ":asc"));
        assertEquals(List.of(second, first), select(List.of(first, second), path + ":desc"));
        var mapper = JsonCodec.createMapper();
        var tree = mapper.valueToTree(second);
        String[] parts = path.split("\\.");
        var filter = mapper.createObjectNode();
        if (parts.length == 1) {
            filter.set(path, tree.get(path));
        } else {
            filter.putObject(parts[0]).set(parts[1], tree.get(parts[0]).get(parts[1]));
        }
        assertEquals(
                List.of(second),
                FlatQuery.of(1, 20, filter, List.of()).select(List.of(first, second)).items());
    }

    @Test
    void nullLastInBothDirectionsAndNullFilters() {
        Flat absent = flat(1, "A", null, null, View.YARD);
        Flat present = flat(2, "B", 2000L, Transport.NONE, View.NORMAL);
        for (String key : List.of("house.year", "transport")) {
            assertEquals(List.of(present, absent), select(List.of(absent, present), key + ":asc"));
            assertEquals(List.of(present, absent), select(List.of(absent, present), key + ":desc"));
        }
        var filter =
                JsonCodec.parse(
                        JsonCodec.createMapper(), "{\"transport\":null,\"house\":{\"year\":null}}");
        assertEquals(
                List.of(absent),
                FlatQuery.of(1, 10, filter, List.of()).select(List.of(absent, present)).items());
    }

    @Test
    void unicodeCodePointOrderObjectsAndIdTieBreaker() {
        Flat bmp = flat(1, "\uE000", 2000L, Transport.FEW, View.YARD);
        Flat supplementary = flat(2, "\uD800\uDC00", 2000L, Transport.FEW, View.YARD);
        assertEquals(List.of(bmp, supplementary), select(List.of(supplementary, bmp), "name:asc"));
        assertEquals(
                List.of(supplementary, bmp), select(List.of(bmp, supplementary), "house:desc"));
        assertEquals(
                List.of(supplementary, bmp),
                select(List.of(bmp, supplementary), "coordinates:desc", "coordinates.x:asc"));
        assertEquals(List.of(bmp, supplementary), select(List.of(supplementary, bmp), "price:asc"));
        assertEquals(
                List.of(bmp, supplementary), select(List.of(supplementary, bmp), "house.year:asc"));
        assertEquals(
                List.of(supplementary, bmp),
                select(List.of(bmp, supplementary), "house.year:asc", "id:desc"));
    }

    @Test
    void enumOrderIsDeclarationOrderAndDecimalEqualityIgnoresScale() {
        Flat good = flat(1, "A", 2000L, Transport.LITTLE, View.GOOD);
        Flat terrible = flat(2, "B", 2000L, Transport.ENOUGH, View.TERRIBLE);
        assertEquals(List.of(good, terrible), select(List.of(terrible, good), "view:asc"));
        var filter = JsonCodec.parse(JsonCodec.createMapper(), "{\"price\":1.00}");
        assertEquals(
                List.of(good),
                FlatQuery.of(1, 20, filter, List.of()).select(List.of(terrible, good)).items());
    }
}
