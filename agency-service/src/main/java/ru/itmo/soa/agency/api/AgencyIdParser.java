package ru.itmo.soa.agency.api;

import ru.itmo.soa.agency.exception.AgencyException;

public final class AgencyIdParser {
    private AgencyIdParser() {}

    public static long parse(String value) {
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
