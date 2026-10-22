package ru.itmo.soa.flats.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.FlatInput;
import ru.itmo.soa.contract.FlatValidator;
import ru.itmo.soa.contract.ValidationException;

import java.util.List;

@Component
@RequiredArgsConstructor
public final class FlatPatchMapper {
    private final ObjectMapper mapper;

    public FlatInput merge(Flat current, ObjectNode patch) {
        ObjectNode merged = editableFields(current);
        applyPatch(merged, patch);
        return readInput(merged);
    }

    private ObjectNode editableFields(Flat current) {
        ObjectNode merged = mapper.valueToTree(current);
        merged.remove(List.of("id", "creationDate"));
        return merged;
    }

    private void applyPatch(ObjectNode merged, ObjectNode patch) {
        for (var entry : patch.properties()) {
            if (entry.getValue() instanceof ObjectNode nested) {
                ((ObjectNode) merged.get(entry.getKey())).setAll(nested);
            } else {
                merged.set(entry.getKey(), entry.getValue());
            }
        }
    }

    private FlatInput readInput(ObjectNode merged) {
        FlatValidator.validate(merged, FlatValidator.Mode.INPUT);
        try {
            return mapper.treeToValue(merged, FlatInput.class);
        } catch (JsonProcessingException e) {
            throw ValidationException.structure("Некорректный формат квартиры");
        }
    }
}
