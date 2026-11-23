package ru.itmo.soa.flats.service;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.stereotype.Service;
import ru.itmo.soa.contract.FlatOperations;

@Service
@RequiredArgsConstructor
public final class FlatService {
    @Delegate
    private final FlatOperations business;
}
