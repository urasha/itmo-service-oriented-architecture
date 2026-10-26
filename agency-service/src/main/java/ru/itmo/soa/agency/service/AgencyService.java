package ru.itmo.soa.agency.service;

import lombok.RequiredArgsConstructor;

import ru.itmo.soa.contract.Flat;

@RequiredArgsConstructor
public final class AgencyService {
    private final FlatSource flats;

    public Flat getCheapest(long firstId, long secondId) {
        Flat first = flats.get(firstId);
        Flat second = flats.get(secondId);
        if (first.price().compareTo(second.price()) <= 0) {
            return first;
        }
        return second;
    }

    public Flat getMostExpensive(long firstId, long secondId, long thirdId) {
        Flat first = flats.get(firstId);
        Flat second = flats.get(secondId);
        Flat third = flats.get(thirdId);
        Flat mostExpensive = moreExpensive(first, second);
        return moreExpensive(mostExpensive, third);
    }

    private Flat moreExpensive(Flat first, Flat second) {
        if (first.price().compareTo(second.price()) >= 0) {
            return first;
        }
        return second;
    }
}
