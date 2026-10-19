package ru.itmo.soa.agency;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public record AgencySettings(URI internalBaseUrl, URI publicBaseUrl, Path trustStore,
                             Path trustStorePasswordFile, Duration connectTimeout,
                             Duration requestTimeout, String allowedOrigin) {
    public AgencySettings {
        https(internalBaseUrl.toString());
        https(publicBaseUrl.toString());
    }
    public static AgencySettings load() {
        String location = System.getProperty("agency.config", System.getenv("AGENCY_CONFIG"));
        if (location == null || location.isBlank()) throw new IllegalStateException("Set agency.config or AGENCY_CONFIG");
        return load(Path.of(location));
    }

    public static AgencySettings load(Path file) {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(reader); }
        catch (IOException e) { throw new IllegalStateException("Cannot read agency configuration", e); }
        Path directory = file.toAbsolutePath().getParent();
        String origin = required(p, "cors.allowedOrigin");
        URI originUri = https(origin);
        if (!(originUri.getRawPath().isEmpty() || originUri.getRawPath().equals("/")))
            throw new IllegalArgumentException("CORS must be an HTTPS origin without a path");
        origin = originUri.getScheme() + "://" + originUri.getRawAuthority();
        return new AgencySettings(https(required(p, "flats.internalBaseUrl")), https(required(p, "flats.publicBaseUrl")),
                directory.resolve(required(p, "flats.trustStore")).normalize(),
                directory.resolve(required(p, "flats.trustStorePasswordFile")).normalize(),
                timeout(p, "flats.connectTimeoutMillis", "3000"), timeout(p, "flats.requestTimeoutMillis", "5000"), origin);
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing configuration: " + key);
        return value.trim();
    }
    private static Duration timeout(Properties p, String key, String fallback) {
        long millis = Long.parseLong(p.getProperty(key, fallback));
        if (millis < 1 || millis > 300000) throw new IllegalArgumentException("Timeout must be 1..300000 ms: " + key);
        return Duration.ofMillis(millis);
    }
    private static URI https(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Expected an absolute HTTPS URL without credentials, query or fragment");
        return uri;
    }
    public static URI flatUri(URI base, long id) {
        String value = base.toASCIIString();
        return URI.create(value.replaceAll("/+$", "") + "/flats/" + id);
    }
}
