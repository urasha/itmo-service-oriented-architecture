package ru.itmo.soa.agency;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import ru.itmo.soa.contract.JsonCodec;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.*;

@EnabledIfSystemProperty(named = "agency.expectUnavailable", matches = "true")
class AgencyUnavailableIT {
    @Test
    void actualPayaraMapsUnavailableJettyTo502() throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        try (var in = Files.newInputStream(Path.of(System.getProperty("agency.certificate")))) {
            trust.setCertificateEntry(
                    "agency", CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        TrustManagerFactory tm =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(trust);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tm.getTrustManagers(), null);
        HttpClient client =
                HttpClient.newBuilder()
                        .sslContext(ssl)
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        String base = System.getProperty("agency.baseUrl", "https://localhost:8444");
        for (String path :
                List.of("/agency/get-cheapest/1/2", "/agency/get-most-expensive/1/2/3")) {
            var response =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + path))
                                    .timeout(Duration.ofSeconds(15))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(502, response.statusCode(), response.body());
            var error = JsonCodec.createMapper().readTree(response.body());
            assertEquals(2, error.size());
            assertTrue(error.get("timestamp").textValue().endsWith("Z"));
            assertFalse(error.get("message").textValue().isBlank());
        }
        var redirect =
                client.send(
                        HttpRequest.newBuilder(URI.create(base + "/agency/get-cheapest/1/1"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(303, redirect.statusCode());
        var invalid =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(base + "/agency/get-most-expensive/1/2/0"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalid.statusCode());
    }
}
