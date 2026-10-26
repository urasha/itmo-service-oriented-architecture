package ru.itmo.soa.flats.repository;

import org.springframework.stereotype.Repository;

import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.FlatInput;
import ru.itmo.soa.flats.exception.FlatNotFoundException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public final class InMemoryFlatRepository {
    private final Map<Long, Flat> flats = new LinkedHashMap<>();
    private long sequence;

    public synchronized Flat create(FlatInput input, LocalDate date) {
        long id = Math.incrementExact(sequence);
        Flat flat = Flat.from(id, date, input);
        sequence = id;
        flats.put(id, flat);
        return flat;
    }

    public synchronized Flat get(long id) {
        Flat flat = flats.get(id);
        if (flat == null) {
            throw new FlatNotFoundException(id);
        }
        return flat;
    }

    public synchronized Flat replace(long id, FlatInput input) {
        Flat current = get(id);
        Flat updated = Flat.from(current.id(), current.creationDate(), input);
        flats.put(id, updated);
        return updated;
    }

    public synchronized void delete(long id) {
        if (flats.remove(id) == null) {
            throw new FlatNotFoundException(id);
        }
    }

    public synchronized List<Flat> snapshot() {
        return List.copyOf(flats.values());
    }
}
