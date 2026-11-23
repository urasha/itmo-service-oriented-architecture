package ru.itmo.soa.contract;

import java.io.Serializable;
import java.util.List;

public record FlatPage(List<Flat> items, long total) implements Serializable {}
