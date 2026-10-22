package ru.itmo.soa.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

public final class FlatValidator {
    public enum Mode {
        INPUT,
        PATCH,
        FILTER
    }

    private enum Type {
        NAME,
        STRING,
        LONG,
        INT,
        ROOMS,
        PRICE,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        VIEW,
        TRANSPORT,
        DATE,
        COORDINATES,
        HOUSE
    }

    private record Field(Type type, boolean nullable) {
        Field(Type type) {
            this(type, false);
        }
    }

    private static final Map<String, Field> FLAT =
            Map.ofEntries(
                    Map.entry("name", new Field(Type.NAME)),
                    Map.entry("coordinates", new Field(Type.COORDINATES)),
                    Map.entry("area", new Field(Type.LONG)),
                    Map.entry("price", new Field(Type.PRICE)),
                    Map.entry("numberOfRooms", new Field(Type.ROOMS)),
                    Map.entry("new", new Field(Type.BOOLEAN)),
                    Map.entry("view", new Field(Type.VIEW)),
                    Map.entry("transport", new Field(Type.TRANSPORT, true)),
                    Map.entry("house", new Field(Type.HOUSE)));
    private static final Set<String> REQUIRED =
            Set.of("name", "coordinates", "area", "price", "numberOfRooms", "new", "view", "house");
    private static final Map<String, Field> COORDINATES =
            Map.of("x", new Field(Type.FLOAT), "y", new Field(Type.DOUBLE));
    private static final Map<String, Field> HOUSE =
            Map.of(
                    "name",
                    new Field(Type.STRING),
                    "year",
                    new Field(Type.LONG, true),
                    "numberOfFloors",
                    new Field(Type.INT),
                    "numberOfFlatsOnFloor",
                    new Field(Type.INT, true),
                    "numberOfLifts",
                    new Field(Type.INT, true));

    private FlatValidator() {}

    public static void validate(JsonNode value, Mode mode) {
        Map<String, Field> fields = new java.util.HashMap<>(FLAT);
        if (mode == Mode.FILTER) {
            fields.put("id", new Field(Type.LONG));
            fields.put("creationDate", new Field(Type.DATE));
        }
        object(value, fields, REQUIRED, mode, "flat");
    }

    private static void object(
            JsonNode node,
            Map<String, Field> fields,
            Set<String> required,
            Mode mode,
            String path) {
        validateObjectShape(node, mode, path);
        if (mode == Mode.INPUT) {
            validateRequiredFields(node, required, path);
        }
        validateFields(node, fields, mode, path);
    }

    private static void validateObjectShape(JsonNode node, Mode mode, String path) {
        if (!node.isObject()) {
            throw ValidationException.structure(path + ": ожидается объект");
        }
        if (mode == Mode.PATCH && node.isEmpty()) {
            throw ValidationException.structure(path + ": PATCH не может быть пустым");
        }
    }

    private static void validateRequiredFields(JsonNode node, Set<String> required, String path) {
        for (String name : required) {
            if (!node.has(name)) {
                throw ValidationException.structure(
                        path + "." + name + ": обязательное поле отсутствует");
            }
        }
    }

    private static void validateFields(
            JsonNode node, Map<String, Field> fields, Mode mode, String path) {
        for (var entry : node.properties()) {
            String fieldPath = path + "." + entry.getKey();
            Field field = fields.get(entry.getKey());
            if (field == null) {
                throw ValidationException.structure(
                        fieldPath + ": неизвестное или запрещённое поле");
            }
            check(entry.getValue(), field, mode, fieldPath);
        }
    }

    private static void check(JsonNode node, Field field, Mode mode, String path) {
        if (node.isNull()) {
            if (!field.nullable) {
                throw ValidationException.structure(path + ": null недопустим");
            }
            return;
        }
        switch (field.type) {
            case COORDINATES -> object(node, COORDINATES, Set.of("x", "y"), mode, path);
            case HOUSE -> object(node, HOUSE, Set.of("name", "numberOfFloors"), mode, path);
            case NAME, STRING -> validateString(node, field.type, path);
            case LONG, INT, ROOMS -> validateInteger(node, field.type, path);
            case PRICE -> validatePrice(node, path);
            case FLOAT, DOUBLE -> validateFloatingPoint(node, field.type, path);
            case BOOLEAN -> require(node.isBoolean(), path, "boolean");
            case VIEW, TRANSPORT -> validateEnum(node, field.type, path);
            case DATE -> validateDate(node, path);
        }
    }

    private static void validateString(JsonNode node, Type type, String path) {
        require(node.isTextual(), path, "строка");
        if (type == Type.NAME && node.textValue().isEmpty()) {
            fail(path, "строка не может быть пустой");
        }
    }

    private static void validateInteger(JsonNode node, Type type, String path) {
        require(node.isIntegralNumber(), path, "целое число");
        require(
                type == Type.LONG ? node.canConvertToLong() : node.canConvertToInt(),
                path,
                "число в диапазоне типа");
        long number = node.longValue();
        if (number < 1 || (type == Type.ROOMS && number > 8)) {
            fail(path, type == Type.ROOMS ? "должно быть от 1 до 8" : "должно быть больше 0");
        }
    }

    private static void validatePrice(JsonNode node, String path) {
        require(node.isNumber(), path, "число");
        BigDecimal price = node.decimalValue();
        if (price.compareTo(new BigDecimal("0.01")) < 0 || price.stripTrailingZeros().scale() > 2) {
            fail(path, "цена должна быть не меньше 0.01 и кратна 0.01");
        }
    }

    private static void validateFloatingPoint(JsonNode node, Type type, String path) {
        require(node.isNumber(), path, "число");
        require(
                type == Type.FLOAT
                        ? Float.isFinite(node.floatValue())
                        : Double.isFinite(node.doubleValue()),
                path,
                "конечное число в диапазоне типа");
    }

    private static void validateEnum(JsonNode node, Type type, String path) {
        require(node.isTextual(), path, "строка enum");
        try {
            if (type == Type.VIEW) {
                View.valueOf(node.textValue());
            } else {
                Transport.valueOf(node.textValue());
            }
        } catch (IllegalArgumentException e) {
            fail(path, "неизвестное значение enum");
        }
    }

    private static void validateDate(JsonNode node, String path) {
        require(node.isTextual(), path, "дата YYYY-MM-DD");
        try {
            if (!node.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw ValidationException.structure(path + ": неверная дата");
            }
            LocalDate.parse(node.textValue());
        } catch (DateTimeParseException e) {
            throw ValidationException.structure(path + ": неверная дата");
        }
    }

    private static void require(boolean condition, String path, String expected) {
        if (!condition) {
            throw ValidationException.structure(path + ": ожидается " + expected);
        }
    }

    private static void fail(String path, String message) {
        throw ValidationException.constraint(path + ": " + message);
    }
}
