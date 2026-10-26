package ru.itmo.soa.flats;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.api.FlatInputDecoder;
import ru.itmo.soa.flats.mapper.FlatPatchMapper;
import ru.itmo.soa.flats.repository.InMemoryFlatRepository;
import ru.itmo.soa.flats.service.FlatService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;

class FlatConcurrencyTest {
    @Test
    void snapshotKeepsOldValuesAfterRepositoryChanges() {
        var input = new FlatInputDecoder(JsonCodec.createMapper()).input(FlatApiTest.VALID);
        var repository = new InMemoryFlatRepository();
        var service =
                new FlatService(
                        repository,
                        new FlatPatchMapper(JsonCodec.createMapper()),
                        Clock.systemUTC());
        var flat = service.create(input);
        var snapshot = repository.snapshot();
        service.delete(flat.id());
        service.create(input);
        assertEquals(java.util.List.of(flat), snapshot);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void concurrentCreatesAndDeepPatchesDoNotLoseData() throws Exception {
        var decoder = new FlatInputDecoder(JsonCodec.createMapper());
        var repository = new InMemoryFlatRepository();
        var patchMapper = new FlatPatchMapper(JsonCodec.createMapper());
        var service = new FlatService(repository, patchMapper, Clock.systemUTC());
        var anotherService = new FlatService(repository, patchMapper, Clock.systemUTC());
        var input = decoder.input(FlatApiTest.VALID);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var jobs = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 200; i++) {
                jobs.add(() -> service.create(input).id());
            }
            var ids = new HashSet<Long>();
            for (Future<Long> job : executor.invokeAll(jobs)) {
                ids.add(job.get());
            }
            assertEquals(200, ids.size());
            assertEquals(200, repository.snapshot().size());
            long id = service.create(input).id();
            var year = decoder.read("{\"house\":{\"year\":2026}}", FlatValidator.Mode.PATCH);
            var floors =
                    decoder.read("{\"house\":{\"numberOfFloors\":7}}", FlatValidator.Mode.PATCH);
            CountDownLatch start = new CountDownLatch(1);
            var a =
                    executor.submit(
                            () -> {
                                start.await();
                                return service.patch(id, year);
                            });
            var b =
                    executor.submit(
                            () -> {
                                start.await();
                                return anotherService.patch(id, floors);
                            });
            start.countDown();
            a.get();
            b.get();
            assertEquals(2026L, service.get(id).house().year());
            assertEquals(7, service.get(id).house().numberOfFloors());
        } finally {
            executor.shutdownNow();
        }
    }
}
