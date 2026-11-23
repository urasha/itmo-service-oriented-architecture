package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ejb.EJB;
import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;

import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.repository.InMemoryFlatRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Stateless
@Remote(FlatOperations.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class FlatBusinessBean implements FlatOperations {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final ObjectMapper mapper = JsonCodec.createMapper();

    @EJB
    private InMemoryFlatRepository repository;

    @Override
    public Flat create(FlatInput input) {
        return repository.create(input, LocalDate.now(MOSCOW));
    }

    @Override
    public Flat get(long id) {
        return repository.get(id);
    }

    @Override
    public Flat replace(long id, FlatInput input) {
        return repository.replace(id, input);
    }

    @Override
    public Flat patch(long id, String json) {
        var patch = JsonCodec.parse(mapper, json);
        FlatValidator.validate(patch, FlatValidator.Mode.PATCH);
        return repository.patch(id, (ObjectNode) patch);
    }

    @Override
    public void delete(long id) {
        repository.delete(id);
    }

    @Override
    public FlatPage list(int page, int size, String filter, List<String> sorts) {
        var criteria = filter == null ? null : JsonCodec.parse(mapper, filter);
        if (criteria != null) {
            try {
                FlatValidator.validate(criteria, FlatValidator.Mode.FILTER);
            } catch (ValidationException e) {
                throw ValidationException.structure(e.getMessage());
            }
        }
        var query = FlatQuery.of(page, size, criteria, sorts);
        return query.select(repository.snapshot());
    }

    @Override
    public Flat minCreationDate() {
        Flat earliest = null;
        for (Flat flat : repository.snapshot()) {
            if (earliest == null || flat.creationDate().isBefore(earliest.creationDate())) {
                earliest = flat;
            }
        }
        return earliest;
    }

    @Override
    public Flat maxNew() {
        List<Flat> flats = repository.snapshot();
        if (flats.isEmpty()) {
            return null;
        }
        for (Flat flat : flats) {
            if (flat.isNew()) {
                return flat;
            }
        }
        return flats.get(0);
    }

    @Override
    public long count(Transport transport) {
        long count = 0;
        for (Flat flat : repository.snapshot()) {
            if (Objects.equals(flat.transport(), transport)) {
                count++;
            }
        }
        return count;
    }
}
