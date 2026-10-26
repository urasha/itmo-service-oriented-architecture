package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.Transport;
import ru.itmo.soa.contract.ValidationException;
import ru.itmo.soa.contract.View;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FlatFieldRegistry {
    @RequiredArgsConstructor
    public enum Field {
        ID("id"),
        NAME("name"),
        X("coordinates.x"),
        Y("coordinates.y"),
        CREATION_DATE("creationDate"),
        AREA("area"),
        PRICE("price"),
        ROOMS("numberOfRooms"),
        NEW("new"),
        VIEW("view"),
        TRANSPORT("transport"),
        HOUSE_NAME("house.name"),
        HOUSE_YEAR("house.year"),
        FLOORS("house.numberOfFloors"),
        FLATS_ON_FLOOR("house.numberOfFlatsOnFloor"),
        LIFTS("house.numberOfLifts");

        private final String path;

        public Object read(JsonNode node) {
            if (node.isNull()) {
                return null;
            }
            return switch (this) {
                case ID, AREA, HOUSE_YEAR -> node.longValue();
                case ROOMS, FLOORS, FLATS_ON_FLOOR, LIFTS -> node.intValue();
                case X -> node.floatValue();
                case Y -> node.doubleValue();
                case PRICE -> node.decimalValue();
                case NAME, HOUSE_NAME -> node.textValue();
                case CREATION_DATE -> LocalDate.parse(node.textValue());
                case NEW -> node.booleanValue();
                case VIEW -> View.valueOf(node.textValue());
                case TRANSPORT -> Transport.valueOf(node.textValue());
            };
        }

        public boolean matches(Flat flat, Object expected) {
            return compareValues(value(flat), expected, false) == 0;
        }

        public int compare(Flat first, Flat second, boolean descending) {
            return compareValues(value(first), value(second), descending);
        }

        private Object value(Flat flat) {
            return switch (this) {
                case ID -> flat.id();
                case NAME -> flat.name();
                case X -> flat.coordinates().x();
                case Y -> flat.coordinates().y();
                case CREATION_DATE -> flat.creationDate();
                case AREA -> flat.area();
                case PRICE -> flat.price();
                case ROOMS -> flat.numberOfRooms();
                case NEW -> flat.isNew();
                case VIEW -> flat.view();
                case TRANSPORT -> flat.transport();
                case HOUSE_NAME -> flat.house().name();
                case HOUSE_YEAR -> flat.house().year();
                case FLOORS -> flat.house().numberOfFloors();
                case FLATS_ON_FLOOR -> flat.house().numberOfFlatsOnFloor();
                case LIFTS -> flat.house().numberOfLifts();
            };
        }
    }

    private FlatFieldRegistry() {}

    public static Field field(String path) {
        for (Field field : Field.values()) {
            if (field.path.equals(path)) {
                return field;
            }
        }
        throw ValidationException.structure("Неизвестное поле: " + path);
    }

    public static List<Field> expand(String path) {
        return switch (path) {
            case "coordinates" -> List.of(Field.X, Field.Y);
            case "house" ->
                    List.of(
                            Field.HOUSE_NAME,
                            Field.HOUSE_YEAR,
                            Field.FLOORS,
                            Field.FLATS_ON_FLOOR,
                            Field.LIFTS);
            default -> List.of(field(path));
        };
    }

    private static int compareValues(Object first, Object second, boolean descending) {
        if (first == second) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        int result = compareNonNull(first, second);
        if (descending) {
            return -Integer.signum(result);
        }
        return Integer.signum(result);
    }

    private static int compareNonNull(Object first, Object second) {
        if (first instanceof Number a && second instanceof Number b) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        }
        if (first instanceof String a && second instanceof String b) {
            return compareUnicode(a, b);
        }
        if (first instanceof Boolean a && second instanceof Boolean b) {
            return Boolean.compare(a, b);
        }
        if (first instanceof LocalDate a && second instanceof LocalDate b) {
            return a.compareTo(b);
        }
        if (first instanceof View a && second instanceof View b) {
            return Integer.compare(a.ordinal(), b.ordinal());
        }
        if (first instanceof Transport a && second instanceof Transport b) {
            return Integer.compare(a.ordinal(), b.ordinal());
        }
        throw new IllegalArgumentException("Несовместимые типы значений поля");
    }

    private static int compareUnicode(String first, String second) {
        int i = 0;
        int j = 0;
        while (i < first.length() && j < second.length()) {
            int left = first.codePointAt(i);
            int right = second.codePointAt(j);
            if (left != right) {
                return Integer.compare(left, right);
            }
            i += Character.charCount(left);
            j += Character.charCount(right);
        }
        return Integer.compare(first.length() - i, second.length() - j);
    }
}
