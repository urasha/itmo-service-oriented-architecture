package ru.itmo.soa.agency;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.itmo.soa.agency.config.AgencySettings;

import java.nio.file.Files;
import java.nio.file.Path;

class AgencySettingsTest {
    @Test
    void configResolvesPathsAndSeparatePublicBase(@TempDir Path directory) throws Exception {
        var file = directory.resolve("agency.properties");
        Files.writeString(
                file,
                "flats.internalBaseUrl=https://internal.example:9443/context/\n"
                        + "flats.publicBaseUrl=https://public.example/\n"
                        + "flats.trustStore=tls/store.p12\n"
                        + "flats.trustStorePasswordFile=tls/store.password\n"
                        + "cors.allowedOrigin=https://client.example\n");
        var settings = AgencySettings.load(file);
        assertEquals(directory.resolve("tls/store.p12"), settings.trustStore());
        assertEquals(
                "https://internal.example:9443/context/flats/12",
                AgencySettings.flatUri(settings.internalBaseUrl(), 12).toString());
        assertEquals(
                "https://public.example/flats/12",
                AgencySettings.flatUri(settings.publicBaseUrl(), 12).toString());
        Files.writeString(
                file, Files.readString(file).replace("https://internal", "http://internal"));
        assertThrows(IllegalArgumentException.class, () -> AgencySettings.load(file));
    }
}
