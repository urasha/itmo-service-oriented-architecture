package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.JsonNode;

import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.flats.exception.ApiException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public record FlatQuery(int page, int size, Predicate<Flat> filter, Comparator<Flat> sort) {
    private record SortKey(String path, boolean descending) {}

    private record Condition(FlatFieldRegistry.Field field, Object expected) {
        boolean matches(Flat flat) {
            return FlatFieldRegistry.compare(field.get().apply(flat), expected, false) == 0;
        }
    }

    public record Page(List<Flat> items, long total) {}

    public static FlatQuery of(int page, int size, JsonNode filter, List<String> sorts) {
        return new FlatQuery(page, size, createFilter(filter), createSort(sorts));
    }

    private static Predicate<Flat> createFilter(JsonNode filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter != null) {
            flatten(filter, "", conditions);
        }
        return flat -> conditions.stream().allMatch(condition -> condition.matches(flat));
    }

    private static Comparator<Flat> createSort(List<String> sorts) {
        List<SortKey> keys = sortKeys(sorts);
        return (first, second) -> compare(first, second, keys);
    }

    private static List<SortKey> sortKeys(List<String> sorts) {
        List<SortKey> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String sort : sorts) {
            SortKey key = parseSortKey(sort);
            if (!seen.add(key.path)) {
                throw ApiException.badRequest("sort: повтор поля " + key.path);
            }
            for (String path : FlatFieldRegistry.expand(key.path)) {
                keys.add(new SortKey(path, key.descending));
            }
        }
        if (!seen.contains("id")) {
            keys.add(new SortKey("id", false));
        }
        return keys;
    }

    private static SortKey parseSortKey(String sort) {
        String[] parts = sort.split(":", -1);
        if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
            throw ApiException.badRequest("sort: ожидается поле:asc либо поле:desc");
        }
        return new SortKey(parts[0], parts[1].equals("desc"));
    }

    private static int compare(Flat first, Flat second, List<SortKey> keys) {
        for (SortKey key : keys) {
            var field = FlatFieldRegistry.field(key.path);
            int result =
                    FlatFieldRegistry.compare(
                            field.get().apply(first), field.get().apply(second), key.descending);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static void flatten(JsonNode node, String prefix, List<Condition> conditions) {
        for (var entry : node.properties()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue().isObject()) {
                flatten(entry.getValue(), path, conditions);
            } else {
                var field = FlatFieldRegistry.field(path);
                conditions.add(new Condition(field, field.value(entry.getValue())));
            }
        }
    }

    public Page select(List<Flat> snapshot) {
        List<Flat> matches = snapshot.stream().filter(filter).sorted(sort).toList();
        long offset = (long) (page - 1) * size;
        if (offset >= matches.size()) {
            return new Page(List.of(), matches.size());
        }
        int start = (int) offset;
        int end = (int) Math.min(offset + size, matches.size());
        return new Page(matches.subList(start, end), matches.size());
    }
}
