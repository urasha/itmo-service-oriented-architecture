package ru.itmo.soa.contract;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record FlatInput(
        String name,
        Coordinates coordinates,
        long area,
        BigDecimal price,
        int numberOfRooms,
        @JsonProperty("new") boolean isNew,
        View view,
        Transport transport,
        House house) implements Serializable {}
