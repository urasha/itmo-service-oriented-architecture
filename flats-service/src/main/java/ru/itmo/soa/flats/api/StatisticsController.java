package ru.itmo.soa.flats.api;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.flats.service.FlatService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping(value = "/flats", produces = "application/json")
@RequiredArgsConstructor
public final class StatisticsController {
    private final FlatService service;
    private final FlatInputDecoder decoder;

    @GetMapping("/min-creation-date")
    public ResponseEntity<Flat> minCreationDate() {
        return flatResponse(service.minCreationDate());
    }

    @GetMapping("/max-new")
    public ResponseEntity<Flat> maxNew() {
        return flatResponse(service.maxNew());
    }

    @GetMapping("/count-by-transport")
    public Map<String, Long> count(HttpServletRequest request) {
        QueryParser query = new QueryParser(request, Set.of("transport"), Set.of());
        return Map.of("count", service.count(decoder.transport(query.required("transport"))));
    }

    private ResponseEntity<Flat> flatResponse(Optional<Flat> flat) {
        if (flat.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(flat.get());
    }
}
