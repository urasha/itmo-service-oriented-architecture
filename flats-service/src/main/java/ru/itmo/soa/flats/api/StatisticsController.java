package ru.itmo.soa.flats.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.flats.service.FlatService;

@RestController
@RequestMapping(value = "/flats", produces = "application/json")
public final class StatisticsController {
    private final FlatService service;
    private final FlatInputDecoder decoder;
    public StatisticsController(FlatService service, FlatInputDecoder decoder) { this.service = service; this.decoder = decoder; }
    @GetMapping("/min-creation-date")
    public ResponseEntity<Flat> minCreationDate() {
        return service.minCreationDate().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @GetMapping("/max-new")
    public ResponseEntity<Flat> maxNew() {
        return service.maxNew().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @GetMapping("/count-by-transport")
    public Map<String, Long> count(HttpServletRequest request) {
        QueryParser query = new QueryParser(request, Set.of("transport"), Set.of());
        return Map.of("count", service.count(decoder.transport(query.required("transport"))));
    }
}
