package ru.itmo.soa.contract;

public record House(
        String name,
        Long year,
        int numberOfFloors,
        Integer numberOfFlatsOnFloor,
        Integer numberOfLifts) {}
