package ru.itmo.soa.flats;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.itmo.soa.contract.JsonCodec;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.*;

import javax.net.ssl.*;

class JettyHttpsIT {
    private final ObjectMapper mapper = JsonCodec.createMapper();
    private final Set<String> created = new HashSet<>();
    private HttpClient client;
    private SSLContext ssl;
    private String base;

    @BeforeEach
    void setup() throws Exception {
        base = System.getProperty("flats.baseUrl", "https://localhost:8443");
        assertEquals("https", URI.create(base).getScheme());
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        Path certificate = Path.of(System.getProperty("flats.certificate"));
        try (var input = Files.newInputStream(certificate)) {
            trust.setCertificateEntry(
                    "flats", CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);
        ssl = SSLContext.getInstance("TLS");
        ssl.init(null, factory.getTrustManagers(), null);
        client =
                HttpClient.newBuilder()
                        .sslContext(ssl)
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (client != null) {
            for (String path : created) {
                request("DELETE", path);
            }
        }
    }

    private HttpResponse<String> request(String method, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/json")
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }

    private String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void error(HttpResponse<String> response, int expected) throws Exception {
        assertEquals(expected, response.statusCode(), response.body());
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .startsWith("application/json"));
        JsonNode error = json(response);
        assertEquals(2, error.size());
        assertTrue(error.get("timestamp").textValue().endsWith("Z"));
        assertFalse(error.get("message").textValue().isBlank());
    }

    @Test
    void allNineOperationsWorkOverVerifiedTls() throws Exception {
        var list = request("GET", "/flats");
        assertEquals(200, list.statusCode(), list.body());
        assertTrue(
                list.headers()
                        .firstValue("Server")
                        .orElse("")
                        .toLowerCase(Locale.ROOT)
                        .contains("jetty"));
        assertTrue(
                Set.of("TLSv1.2", "TLSv1.3")
                        .contains(list.sslSession().orElseThrow().getProtocol()));
        String name = "HTTPS + & # " + UUID.randomUUID();
        String input = FlatApiTest.VALID.replace("Квартира на Невском", name);
        var post = request("POST", "/flats?flat=" + query(input));
        assertEquals(201, post.statusCode(), post.body());
        assertEquals("", post.body());
        String path = post.headers().firstValue("Location").orElseThrow();
        created.add(path);
        JsonNode initial = json(request("GET", path));
        assertEquals(name, initial.get("name").textValue());
        var filtered =
                request(
                        "GET",
                        "/flats?filter="
                                + query("{\"name\":\"" + name + "\"}")
                                + "&sort=area:desc&sort=name:asc&page=1&size=1");
        assertEquals("1", filtered.headers().firstValue("X-Total-Count").orElseThrow());
        assertEquals(initial, json(filtered).get(0));
        var patch =
                request(
                        "PATCH",
                        path
                                + "?flat="
                                + query(
                                        "{\"new\":true,\"coordinates\":{\"x\":11},\"house\":{\"year\":2026}}"));
        assertEquals(200, patch.statusCode(), patch.body());
        assertTrue(json(patch).get("new").booleanValue());
        assertEquals(initial.get("coordinates").get("y"), json(patch).get("coordinates").get("y"));
        assertEquals(200, request("GET", "/flats/min-creation-date").statusCode());
        assertTrue(json(request("GET", "/flats/max-new")).get("new").booleanValue());
        assertTrue(
                json(request("GET", "/flats/count-by-transport?transport=null"))
                                .get("count")
                                .longValue()
                        >= 1);
        var put = request("PUT", path + "?flat=" + query(input));
        assertEquals(200, put.statusCode(), put.body());
        assertEquals(initial, json(put));
        var delete = request("DELETE", path);
        assertEquals(204, delete.statusCode());
        assertEquals("", delete.body());
        error(request("GET", path), 404);
        created.remove(path);
    }

    @Test
    void invalidRequestsHaveContractStatusAndJson() throws Exception {
        error(request("POST", "/flats"), 400);
        error(
                request(
                        "POST",
                        "/flats?flat="
                                + query(FlatApiTest.VALID.replace("\"area\":54", "\"area\":0"))),
                422);
        error(request("POST", "/flats?flat=" + query("{\"id\":1}")), 400);
        error(request("GET", "/flats/9223372036854775808"), 400);
        error(request("GET", "/flats?sort=area:asc&sort=area:desc"), 400);
        error(request("GET", "/flats?filter=" + query("{\"house\":{\"year\":0}}")), 400);
        error(request("GET", "/flats/count-by-transport?transport=NONE"), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void uriLimitWorksBeforeAndAfterContainerParsing(String method) throws Exception {
        String target = method.equals("POST") ? "/flats" : "/flats/1";
        error(request(method, target + "?flat=" + "a".repeat(20000)), 414);
        error(request(method, target + "?flat=" + "a".repeat(100000)), 414);
    }

    @Test
    void plaintextAndUntrustedCertificatesAreRejected() throws Exception {
        var plain = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var request =
                HttpRequest.newBuilder(URI.create(base.replaceFirst("https:", "http:") + "/flats"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
        assertThrows(
                IOException.class, () -> plain.send(request, HttpResponse.BodyHandlers.ofString()));
        var https =
                HttpRequest.newBuilder(URI.create(base + "/flats"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
        assertThrows(
                SSLHandshakeException.class,
                () -> plain.send(https, HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void hostnameVerificationIsNotDisabled() throws Exception {
        URI uri = URI.create(base);
        int port = uri.getPort() < 0 ? 443 : uri.getPort();
        try (Socket transport = new Socket(uri.getHost(), port);
                SSLSocket socket =
                        (SSLSocket)
                                ssl.getSocketFactory()
                                        .createSocket(
                                                transport, "wrong-host.invalid", port, true)) {
            socket.setSoTimeout(3000);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            parameters.setServerNames(List.of(new SNIHostName("wrong-host.invalid")));
            socket.setSSLParameters(parameters);
            assertThrows(SSLHandshakeException.class, socket::startHandshake);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TLSv1.2", "TLSv1.3"})
    void supportedProtocolsVerifyTheCertificate(String protocol) throws Exception {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[] {protocol});
        var tlsClient =
                HttpClient.newBuilder()
                        .sslContext(ssl)
                        .sslParameters(parameters)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build();
        var response =
                tlsClient.send(
                        HttpRequest.newBuilder(URI.create(base + "/flats"))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(protocol, response.sslSession().orElseThrow().getProtocol());
    }
}
