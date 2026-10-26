package ru.itmo.soa.flats.service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.ValidationException;
import ru.itmo.soa.flats.service.FlatFieldRegistry.Field;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlatQuery {
    private final int page;
    private final int size;
    private final List<Condition> conditions;
    private final List<SortKey> sortKeys;

    private record Condition(Field field, Object expected) {
        boolean matches(Flat flat) {
            return field.matches(flat, expected);
        }
    }

    private record SortKey(Field field, boolean descending) {}

    public record Page(List<Flat> items, long total) {}

    public static FlatQuery of(int page, int size, JsonNode filter, List<String> sorts) {
        return new FlatQuery(page, size, readConditions(filter), readSortKeys(sorts));
    }

    public Page select(List<Flat> snapshot) {
        List<Flat> matches = filter(snapshot);
        matches.sort(new FlatComparator(sortKeys));
        return selectPage(matches);
    }

    private List<Flat> filter(List<Flat> snapshot) {
        List<Flat> result = new ArrayList<>();
        for (Flat flat : snapshot) {
            if (matchesAllConditions(flat)) {
                result.add(flat);
            }
        }
        return result;
    }

    private boolean matchesAllConditions(Flat flat) {
        for (Condition condition : conditions) {
            if (!condition.matches(flat)) {
                return false;
            }
        }
        return true;
    }

    private Page selectPage(List<Flat> matches) {
        long offset = (long) (page - 1) * size;
        if (offset >= matches.size()) {
            return new Page(List.of(), matches.size());
        }
        int start = (int) offset;
        int end = (int) Math.min(offset + size, matches.size());
        return new Page(List.copyOf(matches.subList(start, end)), matches.size());
    }

    private static List<Condition> readConditions(JsonNode filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter != null) {
            collectConditions(filter, "", conditions);
        }
        return List.copyOf(conditions);
    }

    private static void collectConditions(
            JsonNode node, String prefix, List<Condition> conditions) {
        for (var entry : node.properties()) {
            String path = entry.getKey();
            if (!prefix.isEmpty()) {
                path = prefix + "." + path;
            }
            if (entry.getValue().isObject()) {
                collectConditions(entry.getValue(), path, conditions);
            } else {
                Field field = FlatFieldRegistry.field(path);
                conditions.add(new Condition(field, field.read(entry.getValue())));
            }
        }
    }

    private static List<SortKey> readSortKeys(List<String> sorts) {
        List<SortKey> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String sort : sorts) {
            addSortKey(sort, keys, seen);
        }
        if (!seen.contains("id")) {
            keys.add(new SortKey(Field.ID, false));
        }
        return List.copyOf(keys);
    }

    private static void addSortKey(String sort, List<SortKey> keys, Set<String> seen) {
        String[] parts = sort.split(":", -1);
        if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
            throw ValidationException.structure("sort: ожидается поле:asc либо поле:desc");
        }
        String path = parts[0];
        if (!seen.add(path)) {
            throw ValidationException.structure("sort: повтор поля " + path);
        }
        boolean descending = parts[1].equals("desc");
        for (Field field : FlatFieldRegistry.expand(path)) {
            keys.add(new SortKey(field, descending));
        }
    }

    @RequiredArgsConstructor
    private static final class FlatComparator implements Comparator<Flat> {
        private final List<SortKey> keys;

        @Override
        public int compare(Flat first, Flat second) {
            for (SortKey key : keys) {
                int result = key.field.compare(first, second, key.descending);
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }
    }
}
