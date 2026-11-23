package ru.itmo.soa.flats.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.exception.ApiException;

@Component
@RequiredArgsConstructor
public final class FlatInputDecoder {
    private final ObjectMapper mapper;

    public FlatInput input(String json) {
        JsonNode node = JsonCodec.parse(mapper, json);
        FlatValidator.validate(node, FlatValidator.Mode.INPUT);
        try {
            return mapper.treeToValue(node, FlatInput.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Некорректный формат квартиры");
        }
    }

    public Transport transport(String json) {
        JsonNode node = JsonCodec.parse(mapper, json);
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.badRequest("transport: ожидается JSON-строка enum либо null");
        }
        try {
            return Transport.valueOf(node.textValue());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("transport: неизвестное значение enum");
        }
    }
}
