package ru.itmo.soa.agency.service;

import ru.itmo.soa.contract.Flat;

public interface FlatSource {
    Flat get(long id);
}
