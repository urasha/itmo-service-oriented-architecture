package ru.itmo.soa.flats.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import ru.itmo.soa.contract.*;

@Component
public final class FlatInputDecoder {
    private final ObjectMapper mapper;
    public FlatInputDecoder(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectNode read(String json, FlatValidator.Mode mode) {
        try {
            JsonNode node = JsonCodec.parse(mapper, json);
            FlatValidator.validate(node, mode);
            return (ObjectNode) node;
        } catch (ValidationException e) {
            if (mode == FlatValidator.Mode.FILTER) throw ApiException.badRequest(e.getMessage());
            throw e;
        }
    }

    public FlatInput input(ObjectNode node) {
        FlatValidator.validate(node, FlatValidator.Mode.INPUT);
        try { return mapper.treeToValue(node, FlatInput.class); }
        catch (JsonProcessingException e) { throw ApiException.badRequest("Некорректный формат квартиры"); }
    }

    public FlatInput merge(Flat old, ObjectNode patch) {
        ObjectNode full = mapper.valueToTree(old);
        full.remove(java.util.List.of("id", "creationDate"));
        patch.properties().forEach(entry -> {
            String name = entry.getKey();
            if (entry.getValue().isObject()) ((ObjectNode) full.get(name)).setAll((ObjectNode) entry.getValue());
            else full.set(name, entry.getValue());
        });
        return input(full);
    }

    public Transport transport(String json) {
        JsonNode node = JsonCodec.parse(mapper, json);
        if (node.isNull()) return null;
        if (!node.isTextual()) throw ApiException.badRequest("transport: ожидается JSON-строка enum либо null");
        try { return Transport.valueOf(node.textValue()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("transport: неизвестное значение enum"); }
    }
}
