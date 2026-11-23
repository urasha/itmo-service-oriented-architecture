package ru.itmo.soa.contract;

import java.io.Serializable;

public record House(
        String name,
        Long year,
        int numberOfFloors,
        Integer numberOfFlatsOnFloor,
        Integer numberOfLifts) implements Serializable {}
