package ru.itmo.soa.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlatValidatorTest {
    private final ObjectMapper mapper = JsonCodec.createMapper();

    private ObjectNode input() {
        return (ObjectNode)
                JsonCodec.parse(
                        mapper,
                        "{\"name\":\"A\",\"coordinates\":{\"x\":1,\"y\":2},\"area\":1,\"price\":1.000,\"numberOfRooms\":1,\"new\":false,\"view\":\"YARD\",\"house\":{\"name\":\"\",\"numberOfFloors\":1}}");
    }

    @Test
    void optionalHouseFieldsAndFalseAreValid() {
        assertDoesNotThrow(() -> FlatValidator.validate(input(), FlatValidator.Mode.INPUT));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "name",
                "coordinates",
                "area",
                "price",
                "numberOfRooms",
                "new",
                "view",
                "house"
            })
    void rejectsEveryMissingRequiredField(String field) {
        ObjectNode input = input();
        input.remove(field);
        assertFalse(
                assertThrows(
                                ValidationException.class,
                                () -> FlatValidator.validate(input, FlatValidator.Mode.INPUT))
                        .isConstraint());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":1}",
                "{\"creationDate\":\"2026-10-12\"}",
                "{\"extra\":1}",
                "{}",
                "{\"coordinates\":{}}",
                "{\"house\":{}}",
                "{\"new\":null}",
                "{\"coordinates\":null}",
                "{\"area\":\"10\"}",
                "{\"area\":9223372036854775808}",
                "{\"numberOfRooms\":2147483648}",
                "{\"coordinates\":{\"x\":1e100}}"
            })
    void distinguishesStructuralPatchErrors(String json) {
        assertFalse(
                assertThrows(
                                ValidationException.class,
                                () ->
                                        FlatValidator.validate(
                                                JsonCodec.parse(mapper, json),
                                                FlatValidator.Mode.PATCH))
                        .isConstraint());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"name\":\"\"}",
                "{\"area\":0}",
                "{\"numberOfRooms\":9}",
                "{\"price\":0.001}",
                "{\"price\":-1}",
                "{\"house\":{\"numberOfLifts\":0}}",
                "{\"view\":\"OTHER\"}"
            })
    void distinguishesModelConstraints(String json) {
        assertTrue(
                assertThrows(
                                ValidationException.class,
                                () ->
                                        FlatValidator.validate(
                                                JsonCodec.parse(mapper, json),
                                                FlatValidator.Mode.PATCH))
                        .isConstraint());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"house\":{}}",
                "{\"transport\":null,\"house\":{\"year\":null}}",
                "{\"id\":1,\"creationDate\":\"2024-02-29\"}"
            })
    void supportsPartialFilters(String json) {
        assertDoesNotThrow(
                () ->
                        FlatValidator.validate(
                                JsonCodec.parse(mapper, json), FlatValidator.Mode.FILTER));
    }
}
