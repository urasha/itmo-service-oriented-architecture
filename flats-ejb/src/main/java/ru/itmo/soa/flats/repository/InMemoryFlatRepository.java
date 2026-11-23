package ru.itmo.soa.flats.repository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;

import ru.itmo.soa.contract.JsonCodec;
import ru.itmo.soa.flats.mapper.FlatPatchMapper;

import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.FlatInput;
import ru.itmo.soa.contract.FlatNotFoundException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Lock(LockType.WRITE)
public class InMemoryFlatRepository {
    private final Map<Long, Flat> flats = new LinkedHashMap<>();
    private final FlatPatchMapper patchMapper = new FlatPatchMapper(JsonCodec.createMapper());
    private long sequence;

    public Flat create(FlatInput input, LocalDate date) {
        long id = Math.incrementExact(sequence);
        Flat flat = Flat.from(id, date, input);
        sequence = id;
        flats.put(id, flat);
        return flat;
    }

    @Lock(LockType.READ)
    public Flat get(long id) {
        Flat flat = flats.get(id);
        if (flat == null) {
            throw new FlatNotFoundException(id);
        }
        return flat;
    }

    public Flat replace(long id, FlatInput input) {
        Flat current = get(id);
        Flat updated = Flat.from(current.id(), current.creationDate(), input);
        flats.put(id, updated);
        return updated;
    }

    public Flat patch(long id, ObjectNode patch) {
        FlatInput updated = patchMapper.merge(get(id).toInput(), patch);
        return replace(id, updated);
    }

    public void delete(long id) {
        if (flats.remove(id) == null) {
            throw new FlatNotFoundException(id);
        }
    }

    @Lock(LockType.READ)
    public List<Flat> snapshot() {
        return List.copyOf(flats.values());
    }
}
