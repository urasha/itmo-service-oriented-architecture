package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.flats.api.ApiException;

public record FlatQuery(int page, int size, Predicate<Flat> filter, Comparator<Flat> sort) {
    private record SortKey(String path, boolean descending) {}
    private record Condition(FlatFieldRegistry.Field field, Object expected) {}
    public record Page(List<Flat> items, long total) {}

    public static FlatQuery of(int page, int size, JsonNode filter, List<String> sorts) {
        List<Condition> conditions = new ArrayList<>();
        if (filter != null) flatten(filter, "", conditions);
        List<SortKey> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String sort : sorts) {
            String[] parts = sort.split(":", -1);
            if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc")))
                throw ApiException.badRequest("sort: ожидается поле:asc либо поле:desc");
            if (!seen.add(parts[0])) throw ApiException.badRequest("sort: повтор поля " + parts[0]);
            for (String path : FlatFieldRegistry.expand(parts[0])) keys.add(new SortKey(path, parts[1].equals("desc")));
        }
        if (!seen.contains("id")) keys.add(new SortKey("id", false));
        Comparator<Flat> comparator = (a, b) -> {
            for (SortKey key : keys) {
                var field = FlatFieldRegistry.field(key.path);
                int result = FlatFieldRegistry.compare(field.get().apply(a), field.get().apply(b), key.descending);
                if (result != 0) return result;
            }
            return 0;
        };
        Predicate<Flat> predicate = flat -> conditions.stream().allMatch(condition ->
                FlatFieldRegistry.compare(condition.field.get().apply(flat), condition.expected, false) == 0);
        return new FlatQuery(page, size, predicate, comparator);
    }

    private static void flatten(JsonNode node, String prefix, List<Condition> conditions) {
        node.properties().forEach(entry -> {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue().isObject()) flatten(entry.getValue(), path, conditions);
            else {
                var field = FlatFieldRegistry.field(path);
                conditions.add(new Condition(field, field.value(entry.getValue())));
            }
        });
    }

    public Page select(List<Flat> snapshot) {
        List<Flat> matches = snapshot.stream().filter(filter).sorted(sort).toList();
        long offset = (long) (page - 1) * size;
        if (offset >= matches.size()) return new Page(List.of(), matches.size());
        int start = (int) offset;
        int end = (int) Math.min(offset + size, matches.size());
        return new Page(matches.subList(start, end), matches.size());
    }
}
