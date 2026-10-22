package ru.itmo.soa.agency.service;

import lombok.RequiredArgsConstructor;

import ru.itmo.soa.agency.exception.AgencyException;
import ru.itmo.soa.contract.Flat;

import java.util.function.LongFunction;

@RequiredArgsConstructor
public final class AgencyService {
    private final LongFunction<Flat> flats;

    public Flat select(boolean cheapest, long... ids) {
        Flat result = flats.apply(ids[0]);
        for (int i = 1; i < ids.length; i++) {
            Flat candidate = flats.apply(ids[i]);
            int comparison = candidate.price().compareTo(result.price());
            if (cheapest ? comparison < 0 : comparison > 0) {
                result = candidate;
            }
        }
        return result;
    }

    public static long id(String value) {
        try {
            if (!value.matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new AgencyException(
                    400, "Идентификатор должен быть целым числом от 1 до 9223372036854775807");
        }
    }
}
