package ru.itmo.soa.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Checks the wire structure before DTO binding, including absent versus null fields. */
public final class FlatValidator {
    public enum Mode { INPUT, PATCH, FILTER }
    private enum Type { NAME, STRING, LONG, INT, ROOMS, PRICE, FLOAT, DOUBLE, BOOLEAN, VIEW, TRANSPORT, DATE, COORDINATES, HOUSE }
    private record Field(Type type, boolean nullable) {
        Field(Type type) { this(type, false); }
    }

    private static final Map<String, Field> FLAT = Map.ofEntries(
            Map.entry("name", new Field(Type.NAME)), Map.entry("coordinates", new Field(Type.COORDINATES)),
            Map.entry("area", new Field(Type.LONG)), Map.entry("price", new Field(Type.PRICE)),
            Map.entry("numberOfRooms", new Field(Type.ROOMS)), Map.entry("new", new Field(Type.BOOLEAN)),
            Map.entry("view", new Field(Type.VIEW)), Map.entry("transport", new Field(Type.TRANSPORT, true)),
            Map.entry("house", new Field(Type.HOUSE)));
    private static final Set<String> REQUIRED = Set.of("name", "coordinates", "area", "price", "numberOfRooms", "new", "view", "house");
    private static final Map<String, Field> COORDINATES = Map.of("x", new Field(Type.FLOAT), "y", new Field(Type.DOUBLE));
    private static final Map<String, Field> HOUSE = Map.of(
            "name", new Field(Type.STRING), "year", new Field(Type.LONG, true),
            "numberOfFloors", new Field(Type.INT), "numberOfFlatsOnFloor", new Field(Type.INT, true),
            "numberOfLifts", new Field(Type.INT, true));

    private FlatValidator() {}

    public static void validate(JsonNode value, Mode mode) {
        Map<String, Field> fields = new java.util.HashMap<>(FLAT);
        if (mode == Mode.FILTER) {
            fields.put("id", new Field(Type.LONG));
            fields.put("creationDate", new Field(Type.DATE));
        }
        object(value, fields, REQUIRED, mode, "flat");
    }

    private static void object(JsonNode node, Map<String, Field> fields, Set<String> required, Mode mode, String path) {
        if (!node.isObject()) throw ValidationException.structure(path + ": ожидается объект");
        if (mode == Mode.PATCH && node.isEmpty()) throw ValidationException.structure(path + ": PATCH не может быть пустым");
        if (mode == Mode.INPUT) for (String name : required) {
            if (!node.has(name)) throw ValidationException.structure(path + "." + name + ": обязательное поле отсутствует");
        }
        node.properties().forEach(entry -> {
            String name = entry.getKey();
            Field field = fields.get(name);
            if (field == null) throw ValidationException.structure(path + "." + name + ": неизвестное или запрещённое поле");
            check(entry.getValue(), field, mode, path + "." + name);
        });
    }

    private static void check(JsonNode node, Field field, Mode mode, String path) {
        if (node.isNull()) {
            if (!field.nullable) throw ValidationException.structure(path + ": null недопустим");
            return;
        }
        switch (field.type) {
            case COORDINATES -> object(node, COORDINATES, Set.of("x", "y"), mode, path);
            case HOUSE -> object(node, HOUSE, Set.of("name", "numberOfFloors"), mode, path);
            case NAME, STRING -> {
                require(node.isTextual(), path, "строка");
                if (field.type == Type.NAME && node.textValue().isEmpty()) fail(path, "строка не может быть пустой");
            }
            case LONG, INT, ROOMS -> {
                require(node.isIntegralNumber(), path, "целое число");
                require(field.type == Type.LONG ? node.canConvertToLong() : node.canConvertToInt(), path, "число в диапазоне типа");
                long number = node.longValue();
                if (number < 1 || (field.type == Type.ROOMS && number > 8)) fail(path, field.type == Type.ROOMS ? "должно быть от 1 до 8" : "должно быть больше 0");
            }
            case PRICE -> {
                require(node.isNumber(), path, "число");
                BigDecimal price = node.decimalValue();
                if (price.compareTo(new BigDecimal("0.01")) < 0 || price.stripTrailingZeros().scale() > 2)
                    fail(path, "цена должна быть не меньше 0.01 и кратна 0.01");
            }
            case FLOAT, DOUBLE -> {
                require(node.isNumber(), path, "число");
                require(field.type == Type.FLOAT ? Float.isFinite(node.floatValue()) : Double.isFinite(node.doubleValue()), path, "конечное число в диапазоне типа");
            }
            case BOOLEAN -> require(node.isBoolean(), path, "boolean");
            case VIEW, TRANSPORT -> {
                require(node.isTextual(), path, "строка enum");
                try {
                    if (field.type == Type.VIEW) View.valueOf(node.textValue());
                    else Transport.valueOf(node.textValue());
                } catch (IllegalArgumentException e) { fail(path, "неизвестное значение enum"); }
            }
            case DATE -> {
                require(node.isTextual(), path, "дата YYYY-MM-DD");
                try {
                    if (!node.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("date", "", 0);
                    LocalDate.parse(node.textValue());
                } catch (DateTimeParseException e) { throw ValidationException.structure(path + ": неверная дата"); }
            }
        }
    }

    private static void require(boolean condition, String path, String expected) {
        if (!condition) throw ValidationException.structure(path + ": ожидается " + expected);
    }

    private static void fail(String path, String message) { throw ValidationException.constraint(path + ": " + message); }
}
