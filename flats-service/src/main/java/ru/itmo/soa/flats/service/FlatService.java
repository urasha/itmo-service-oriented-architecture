package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.api.FlatInputDecoder;
import ru.itmo.soa.flats.repository.InMemoryFlatRepository;

@Service
public final class FlatService {
    private final InMemoryFlatRepository repository;
    private final FlatInputDecoder decoder;
    private final Clock clock;
    public FlatService(InMemoryFlatRepository repository, FlatInputDecoder decoder, Clock clock) {
        this.repository = repository; this.decoder = decoder; this.clock = clock;
    }
    public Flat create(FlatInput input) { return repository.create(input, LocalDate.now(clock)); }
    public Flat get(long id) { return repository.get(id); }
    public Flat replace(long id, FlatInput input) {
        return repository.update(id, old -> Flat.from(old.id(), old.creationDate(), input));
    }
    public Flat patch(long id, ObjectNode patch) {
        return repository.update(id, old -> Flat.from(old.id(), old.creationDate(), decoder.merge(old, patch)));
    }
    public void delete(long id) { repository.delete(id); }
    public FlatQuery.Page list(FlatQuery query) { return query.select(repository.snapshot()); }
    public Optional<Flat> minCreationDate() { return repository.snapshot().stream().min(Comparator.comparing(Flat::creationDate)); }
    public Optional<Flat> maxNew() { return repository.snapshot().stream().max(Comparator.comparing(Flat::isNew)); }
    public long count(Transport transport) { return repository.snapshot().stream().filter(f -> Objects.equals(f.transport(), transport)).count(); }
}
