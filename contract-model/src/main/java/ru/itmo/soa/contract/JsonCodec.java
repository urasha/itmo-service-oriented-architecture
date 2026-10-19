package ru.itmo.soa.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonCodec {
    private JsonCodec() {}

    public static ObjectMapper createMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        for (LogicalType type : new LogicalType[]{LogicalType.Integer, LogicalType.Float, LogicalType.Boolean}) {
            mapper.coercionConfigFor(type).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        }
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    public static JsonNode parse(ObjectMapper mapper, String value) {
        if (value == null || value.isBlank()) throw ValidationException.structure("Ожидается JSON-значение");
        try {
            JsonNode node = mapper.readTree(value);
            if (node == null || node.isMissingNode()) throw ValidationException.structure("Ожидается JSON-значение");
            return node;
        } catch (JsonProcessingException e) {
            throw ValidationException.structure("Некорректный JSON");
        }
    }
}
