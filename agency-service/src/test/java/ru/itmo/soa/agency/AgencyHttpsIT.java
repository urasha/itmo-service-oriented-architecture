package ru.itmo.soa.agency;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.itmo.soa.contract.JsonCodec;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.*;

import javax.net.ssl.*;

class AgencyHttpsIT {
    private final ObjectMapper mapper = JsonCodec.createMapper();
    private final List<String> created = new ArrayList<>();
    private HttpClient client;
    private SSLContext ssl;
    private String agency, flats;

    @BeforeEach
    void setup() throws Exception {
        agency = System.getProperty("agency.baseUrl", "https://localhost:8444");
        flats = System.getProperty("flats.baseUrl", "https://localhost:8443");
        assertEquals("https", URI.create(agency).getScheme());
        assertEquals("https", URI.create(flats).getScheme());
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        for (String name : List.of("flats", "agency")) {
            try (var in =
                    Files.newInputStream(Path.of(System.getProperty(name + ".certificate")))) {
                trust.setCertificateEntry(
                        name, CertificateFactory.getInstance("X.509").generateCertificate(in));
            }
        }
        TrustManagerFactory tm =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(trust);
        ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tm.getTrustManagers(), null);
        client =
                HttpClient.newBuilder()
                        .sslContext(ssl)
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (client != null) {
            for (String path : created) {
                request(flats, "DELETE", path);
            }
        }
    }

    private HttpResponse<String> request(String base, String method, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private long create(String price) throws Exception {
        String input = AgencyServiceTest.FLAT.replace("100.01", price);
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(input);
        node.remove(List.of("id", "creationDate"));
        var response =
                request(
                        flats,
                        "POST",
                        "/flats?flat="
                                + URLEncoder.encode(
                                        mapper.writeValueAsString(node), StandardCharsets.UTF_8));
        assertEquals(201, response.statusCode(), response.body());
        String location = response.headers().firstValue("Location").orElseThrow();
        created.add(location);
        return mapper.readTree(request(flats, "GET", location).body()).get("id").longValue();
    }

    private void error(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .startsWith("application/json"));
        var node = mapper.readTree(response.body());
        assertEquals(2, node.size());
        assertFalse(node.get("message").textValue().isBlank());
        java.time.Instant.parse(node.get("timestamp").textValue());
        assertTrue(node.get("timestamp").textValue().endsWith("Z"));
    }

    @Test
    void bothOperationsAndTiesUseActualJettyData() throws Exception {
        long a = create("9007199254740992.01"),
                b = create("9007199254740992.02"),
                c = create("9007199254740992.02");
        var response = request(agency, "GET", "/agency/get-cheapest/" + b + "/" + a);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Server").orElse("").contains("Payara"));
        assertEquals(
                mapper.readTree(request(flats, "GET", "/flats/" + a).body()),
                mapper.readTree(response.body()));
        response = request(agency, "GET", "/agency/get-most-expensive/" + a + "/" + c + "/" + b);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(c, mapper.readTree(response.body()).get("id").longValue());
        response = request(agency, "GET", "/agency/get-cheapest/" + c + "/" + b);
        assertEquals(c, mapper.readTree(response.body()).get("id").longValue());

        var update =
                request(
                        flats,
                        "PATCH",
                        "/flats/"
                                + a
                                + "?flat="
                                + URLEncoder.encode(
                                        "{\"price\":9007199254740992.03}", StandardCharsets.UTF_8));
        assertEquals(200, update.statusCode());
        response = request(agency, "GET", "/agency/get-most-expensive/" + a + "/" + b + "/" + c);
        assertEquals(a, mapper.readTree(response.body()).get("id").longValue());
    }

    @Test
    void redirectsAreEmptyAndPointToFirstPublicService() throws Exception {
        for (String path :
                List.of("/agency/get-cheapest/7/007", "/agency/get-most-expensive/7/7/7")) {
            var response = request(agency, "GET", path);
            assertEquals(303, response.statusCode(), response.body());
            assertEquals("", response.body());
            assertEquals(
                    System.getProperty("flats.publicBaseUrl", flats) + "/flats/7",
                    response.headers().firstValue("Location").orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.0", "9223372036854775808"})
    void invalidIdsAre400BeforeCallingUpstream(String bad) throws Exception {
        error(request(agency, "GET", "/agency/get-cheapest/1/" + bad), 400);
        error(request(agency, "GET", "/agency/get-most-expensive/1/2/" + bad), 400);
    }

    @Test
    void missingObjectsAndBadRoutesHaveJsonErrors() throws Exception {
        error(
                request(
                        agency,
                        "GET",
                        "/agency/get-cheapest/9223372036854775807/9223372036854775806"),
                404);
        error(request(agency, "GET", "/agency/get-most-expensive/9223372036854775807/1/2"), 404);
        error(request(agency, "GET", "/agency/unknown"), 404);
        var response = request(agency, "POST", "/agency/get-cheapest/1/2");
        error(response, 405);
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"));
    }

    @Test
    void corsIsRestrictedToConfiguredOrigin() throws Exception {
        String origin = System.getProperty("agency.allowedOrigin", flats);
        var response =
                client.send(
                        HttpRequest.newBuilder(URI.create(agency + "/agency/get-cheapest/1/1"))
                                .header("Origin", origin)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(
                origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        response =
                client.send(
                        HttpRequest.newBuilder(URI.create(agency + "/agency/get-cheapest/1/1"))
                                .header("Origin", "https://unrelated.example")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        response =
                client.send(
                        HttpRequest.newBuilder(URI.create(agency + "/agency/get-cheapest/1/2"))
                                .header("Origin", origin)
                                .header("Access-Control-Request-Method", "GET")
                                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        assertEquals(
                origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TLSv1.2", "TLSv1.3"})
    void supportsModernTls(String protocol) throws Exception {
        SSLParameters p = new SSLParameters();
        p.setProtocols(new String[] {protocol});
        var c = HttpClient.newBuilder().sslContext(ssl).sslParameters(p).build();
        var response =
                c.send(
                        HttpRequest.newBuilder(URI.create(agency + "/agency/get-cheapest/1/1"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(303, response.statusCode());
        assertEquals(protocol, response.sslSession().orElseThrow().getProtocol());
    }

    @Test
    void certificatesAreRequiredAndPlainHttpApiIsUnavailable() throws Exception {
        assertThrows(
                java.io.IOException.class,
                () ->
                        HttpClient.newHttpClient()
                                .send(
                                        HttpRequest.newBuilder(
                                                        URI.create(
                                                                agency
                                                                        + "/agency/get-cheapest/1/1"))
                                                .GET()
                                                .build(),
                                        HttpResponse.BodyHandlers.ofString()));
        URI base = URI.create(agency);
        try (var tcp = new java.net.Socket(base.getHost(), base.getPort());
                var tls =
                        (SSLSocket)
                                ssl.getSocketFactory()
                                        .createSocket(
                                                tcp, "unrelated.example", base.getPort(), true)) {
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters);
            assertThrows(SSLHandshakeException.class, tls::startHandshake);
        }
        try (var socket = new java.net.Socket(base.getHost(), base.getPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream()
                    .write(
                            "GET /agency/get-cheapest/1/1 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                                    .getBytes(StandardCharsets.US_ASCII));
            try {
                String data =
                        new String(
                                socket.getInputStream().readNBytes(200), StandardCharsets.US_ASCII);
                assertFalse(data.contains("303 See Other"));
                assertFalse(data.contains("200 OK"));
            } catch (java.io.IOException expected) {
            }
        }
        int port = Integer.parseInt(System.getProperty("agency.httpPort", "18080"));
        assertThrows(
                java.io.IOException.class,
                () ->
                        HttpClient.newHttpClient()
                                .send(
                                        HttpRequest.newBuilder(
                                                        URI.create(
                                                                "http://"
                                                                        + base.getHost()
                                                                        + ":"
                                                                        + port
                                                                        + "/agency/get-cheapest/1/1"))
                                                .timeout(Duration.ofSeconds(3))
                                                .GET()
                                                .build(),
                                        HttpResponse.BodyHandlers.ofString()));

        var admin =
                request(
                        "http://"
                                + base.getHost()
                                + ":"
                                + System.getProperty("agency.adminPort", "18048"),
                        "GET",
                        "/agency/get-cheapest/1/1");
        assertTrue(admin.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(admin.headers().firstValue("Location").isEmpty());
    }
}
