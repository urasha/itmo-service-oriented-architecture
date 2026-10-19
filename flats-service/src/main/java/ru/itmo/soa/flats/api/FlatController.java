package ru.itmo.soa.flats.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itmo.soa.contract.*;
import ru.itmo.soa.flats.service.FlatQuery;
import ru.itmo.soa.flats.service.FlatService;

@RestController
@RequestMapping(value = "/flats", produces = "application/json")
public final class FlatController {
    private final FlatService service;
    private final FlatInputDecoder decoder;
    public FlatController(FlatService service, FlatInputDecoder decoder) { this.service = service; this.decoder = decoder; }

    @GetMapping
    public ResponseEntity<List<Flat>> list(HttpServletRequest request) {
        QueryParser query = new QueryParser(request, Set.of("page", "size", "sort", "filter"), Set.of("sort"));
        String filter = query.optional("filter");
        FlatQuery.Page page = service.list(FlatQuery.of(query.positiveInt("page", 1), query.positiveInt("size", 20),
                filter == null ? null : decoder.read(filter, FlatValidator.Mode.FILTER), query.all("sort")));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(page.total())).body(page.items());
    }

    @PostMapping
    public ResponseEntity<Void> create(HttpServletRequest request) {
        Flat flat = service.create(input(request));
        return ResponseEntity.created(URI.create("/flats/" + flat.id())).build();
    }

    @GetMapping("/{id}")
    public Flat get(@PathVariable("id") String id) { return service.get(QueryParser.id(id)); }

    @PutMapping("/{id}")
    public Flat replace(@PathVariable("id") String id, HttpServletRequest request) {
        long parsed = QueryParser.id(id);
        return service.replace(parsed, input(request));
    }

    @PatchMapping("/{id}")
    public Flat patch(@PathVariable("id") String id, HttpServletRequest request) {
        long parsed = QueryParser.id(id);
        QueryParser query = new QueryParser(request, Set.of("flat"), Set.of());
        return service.patch(parsed, decoder.read(query.required("flat"), FlatValidator.Mode.PATCH));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        service.delete(QueryParser.id(id));
        return ResponseEntity.noContent().build();
    }

    private FlatInput input(HttpServletRequest request) {
        QueryParser query = new QueryParser(request, Set.of("flat"), Set.of());
        return decoder.input(decoder.read(query.required("flat"), FlatValidator.Mode.INPUT));
    }
}
