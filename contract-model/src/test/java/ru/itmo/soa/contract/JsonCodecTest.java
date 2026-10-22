package ru.itmo.soa.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

class JsonCodecTest {
    private final ObjectMapper mapper = JsonCodec.createMapper();

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{", "{} {}", "{\"a\":1,\"a\":2}", "NaN", "Infinity"})
    void rejectsMalformedOrAmbiguousJson(String json) {
        assertThrows(ValidationException.class, () -> JsonCodec.parse(mapper, json));
    }

    @Test
    void keepsDecimalAndLongPrecision() {
        var value =
                JsonCodec.parse(
                        mapper, "{\"price\":9007199254740993.01,\"area\":9223372036854775807}");
        assertEquals(
                0,
                new BigDecimal("9007199254740993.01").compareTo(value.get("price").decimalValue()));
        assertEquals(Long.MAX_VALUE, value.get("area").longValue());
    }

    @Test
    void emitsUtcErrorAndJsonNewField() throws Exception {
        ApiError error = new ApiError(Instant.parse("2026-10-12T12:00:00Z"), "Ошибка");
        var node = mapper.readTree(mapper.writeValueAsString(error));
        assertEquals("2026-10-12T12:00:00Z", node.get("timestamp").textValue());
        assertEquals(2, node.size());
        FlatInput input =
                mapper.readValue(
                        "{\"name\":\"A\",\"coordinates\":{\"x\":1,\"y\":2},\"area\":1,\"price\":1,\"numberOfRooms\":1,\"new\":false,\"view\":\"YARD\",\"house\":{\"name\":\"\",\"numberOfFloors\":1}}",
                        FlatInput.class);
        var tree = mapper.valueToTree(input);
        assertTrue(tree.has("new"));
        assertFalse(tree.has("isNew"));
        assertTrue(tree.get("transport").isNull());
    }
}
