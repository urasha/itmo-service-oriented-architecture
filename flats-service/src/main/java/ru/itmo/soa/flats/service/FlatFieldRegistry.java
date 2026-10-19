package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.api.ApiException;

/** Explicit allowed paths; never resolve arbitrary properties supplied by the caller. */
public final class FlatFieldRegistry {
    public record Field(Function<Flat, ?> get, Function<JsonNode, ?> read) {
        public Object value(JsonNode node) { return node.isNull() ? null : read.apply(node); }
    }
    private static final Map<String, List<String>> OBJECTS = Map.of(
            "coordinates", List.of("coordinates.x", "coordinates.y"),
            "house", List.of("house.name", "house.year", "house.numberOfFloors", "house.numberOfFlatsOnFloor", "house.numberOfLifts"));
    private static final Map<String, Field> FIELDS = Map.ofEntries(
            Map.entry("id", new Field(Flat::id, JsonNode::longValue)),
            Map.entry("name", new Field(Flat::name, JsonNode::textValue)),
            Map.entry("coordinates.x", new Field(f -> f.coordinates().x(), JsonNode::floatValue)),
            Map.entry("coordinates.y", new Field(f -> f.coordinates().y(), JsonNode::doubleValue)),
            Map.entry("creationDate", new Field(Flat::creationDate, n -> LocalDate.parse(n.textValue()))),
            Map.entry("area", new Field(Flat::area, JsonNode::longValue)),
            Map.entry("price", new Field(Flat::price, JsonNode::decimalValue)),
            Map.entry("numberOfRooms", new Field(Flat::numberOfRooms, JsonNode::intValue)),
            Map.entry("new", new Field(Flat::isNew, JsonNode::booleanValue)),
            Map.entry("view", new Field(Flat::view, n -> View.valueOf(n.textValue()))),
            Map.entry("transport", new Field(Flat::transport, n -> Transport.valueOf(n.textValue()))),
            Map.entry("house.name", new Field(f -> f.house().name(), JsonNode::textValue)),
            Map.entry("house.year", new Field(f -> f.house().year(), JsonNode::longValue)),
            Map.entry("house.numberOfFloors", new Field(f -> f.house().numberOfFloors(), JsonNode::intValue)),
            Map.entry("house.numberOfFlatsOnFloor", new Field(f -> f.house().numberOfFlatsOnFloor(), JsonNode::intValue)),
            Map.entry("house.numberOfLifts", new Field(f -> f.house().numberOfLifts(), JsonNode::intValue)));

    private FlatFieldRegistry() {}
    public static Field field(String path) {
        Field field = FIELDS.get(path);
        if (field == null) throw ApiException.badRequest("Неизвестное поле: " + path);
        return field;
    }
    public static List<String> expand(String path) {
        if (OBJECTS.containsKey(path)) return OBJECTS.get(path);
        field(path);
        return List.of(path);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object first, Object second, boolean descending) {
        // Nulls stay last even for descending order.
        if (first == second) return 0;
        if (first == null) return 1;
        if (second == null) return -1;
        int result;
        if (first instanceof Number a && second instanceof Number b) {
            result = new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        } else if (first instanceof String a && second instanceof String b) {
            result = compareUnicode(a, b);
        } else result = ((Comparable) first).compareTo(second);
        return descending ? -Integer.signum(result) : Integer.signum(result);
    }

    private static int compareUnicode(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int left = a.codePointAt(i), right = b.codePointAt(j);
            if (left != right) return Integer.compare(left, right);
            i += Character.charCount(left); j += Character.charCount(right);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
