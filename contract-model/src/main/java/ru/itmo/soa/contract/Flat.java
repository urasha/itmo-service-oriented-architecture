package ru.itmo.soa.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record Flat(long id, String name, Coordinates coordinates, LocalDate creationDate,
                   long area, BigDecimal price, int numberOfRooms,
                   @JsonProperty("new") boolean isNew, View view, Transport transport, House house) {
    public static Flat from(long id, LocalDate date, FlatInput input) {
        return new Flat(id, input.name(), input.coordinates(), date, input.area(), input.price(),
                input.numberOfRooms(), input.isNew(), input.view(), input.transport(), input.house());
    }
}
