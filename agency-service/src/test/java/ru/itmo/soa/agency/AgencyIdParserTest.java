package ru.itmo.soa.agency;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.itmo.soa.agency.api.AgencyIdParser;
import ru.itmo.soa.agency.exception.AgencyException;

class AgencyIdParserTest {
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "1.0", "abc", "9223372036854775808", " ", "1e2"})
    void rejectsBadIdsAs400(String value) {
        assertEquals(
                400,
                assertThrows(AgencyException.class, () -> AgencyIdParser.parse(value)).status());
    }

    @Test
    void acceptsInt64Boundary() {
        assertEquals(Long.MAX_VALUE, AgencyIdParser.parse("9223372036854775807"));
        assertEquals(1, AgencyIdParser.parse("001"));
    }
}
