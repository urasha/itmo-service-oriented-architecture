package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.mapper.FlatPatchMapper;
import ru.itmo.soa.flats.repository.InMemoryFlatRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public final class FlatService {
    private final InMemoryFlatRepository repository;
    private final FlatPatchMapper patchMapper;
    private final Clock clock;

    public Flat create(FlatInput input) {
        return repository.create(input, LocalDate.now(clock));
    }

    public Flat get(long id) {
        return repository.get(id);
    }

    public Flat replace(long id, FlatInput input) {
        return repository.replace(id, input);
    }

    public Flat patch(long id, ObjectNode patch) {
        synchronized (repository) {
            Flat current = repository.get(id);
            FlatInput updated = patchMapper.merge(current.toInput(), patch);
            return repository.replace(id, updated);
        }
    }

    public void delete(long id) {
        repository.delete(id);
    }

    public FlatQuery.Page list(FlatQuery query) {
        return query.select(repository.snapshot());
    }

    public Optional<Flat> minCreationDate() {
        Flat earliest = null;
        for (Flat flat : repository.snapshot()) {
            if (earliest == null || flat.creationDate().isBefore(earliest.creationDate())) {
                earliest = flat;
            }
        }
        return Optional.ofNullable(earliest);
    }

    public Optional<Flat> maxNew() {
        List<Flat> flats = repository.snapshot();
        if (flats.isEmpty()) {
            return Optional.empty();
        }
        for (Flat flat : flats) {
            if (flat.isNew()) {
                return Optional.of(flat);
            }
        }
        return Optional.of(flats.get(0));
    }

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
