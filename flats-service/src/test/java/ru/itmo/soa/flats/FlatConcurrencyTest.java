package ru.itmo.soa.flats;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;
import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.api.FlatInputDecoder;
import ru.itmo.soa.flats.repository.InMemoryFlatRepository;
import ru.itmo.soa.flats.service.FlatService;
import static org.junit.jupiter.api.Assertions.*;

class FlatConcurrencyTest {
    @Test void concurrentCreatesAndDeepPatchesDoNotLoseData() throws Exception {
        var decoder = new FlatInputDecoder(JsonCodec.createMapper());
        var repository = new InMemoryFlatRepository();
        var service = new FlatService(repository, decoder, Clock.systemUTC());
        var input = decoder.input(decoder.read(FlatApiTest.VALID, FlatValidator.Mode.INPUT));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var jobs = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 200; i++) jobs.add(() -> service.create(input).id());
            var ids = new HashSet<Long>();
            for (Future<Long> job : executor.invokeAll(jobs)) ids.add(job.get());
            assertEquals(200, ids.size()); assertEquals(200, repository.snapshot().size());
            long id = service.create(input).id();
            var year = decoder.read("{\"house\":{\"year\":2026}}", FlatValidator.Mode.PATCH);
            var floors = decoder.read("{\"house\":{\"numberOfFloors\":7}}", FlatValidator.Mode.PATCH);
            CountDownLatch start = new CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return service.patch(id, year); });
            var b = executor.submit(() -> { start.await(); return service.patch(id, floors); });
            start.countDown(); a.get(); b.get();
            assertEquals(2026L, service.get(id).house().year());
            assertEquals(7, service.get(id).house().numberOfFloors());
        } finally { executor.shutdownNow(); }
    }
}
