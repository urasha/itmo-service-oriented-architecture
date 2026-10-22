import java.net.Socket;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;

import javax.net.ssl.*;

class Smoke {
    static HttpClient client;

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "flatsInternalBase agencyInternalBase flatsCertificate agencyCertificate"
                            + " flatsPublicBase");
        }
        configureClient(args[2], args[3]);
        get(args[0] + "/flats", 200);
        var app = get(args[0] + "/app/index.html", 200);
        if (!app.body().contains("./app.js")) {
            throw new IllegalStateException("Client HTML is missing");
        }
        get(args[0] + "/app/config.js", 200);
        get(args[0] + "/docs/index.html", 200);
        var redirect = get(args[1] + "/agency/get-cheapest/1/1", 303);
        if (!redirect.headers()
                .firstValue("Location")
                .orElse("")
                .equals(args[4].replaceAll("/+$", "") + "/flats/1")) {
            throw new IllegalStateException("Incorrect public Location");
        }
        get(args[1] + "/agency/get-cheapest/9223372036854775807/9223372036854775806", 404);
        verifyPlainHttpRejected(args[0], "/flats");
        verifyPlainHttpRejected(args[1], "/agency/get-cheapest/1/1");
        System.out.println(
                "PASS HTTPS, client, Swagger, agency redirect/upstream and plaintext rejection."
                        + " Browser CORS and Helios OS compatibility still require target"
                        + " verification.");
    }

    private static void configureClient(String... certificates) throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        for (int i = 0; i < certificates.length; i++) {
            try (var in = Files.newInputStream(Path.of(certificates[i]))) {
                trust.setCertificateEntry(
                        "service" + i,
                        CertificateFactory.getInstance("X.509").generateCertificate(in));
            }
        }
        TrustManagerFactory managers =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, managers.getTrustManagers(), null);
        client =
                HttpClient.newBuilder()
                        .sslContext(ssl)
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    private static void verifyPlainHttpRejected(String url, String path) throws Exception {
        URI base = URI.create(url);
        try (Socket socket =
                new Socket(base.getHost(), base.getPort() == -1 ? 443 : base.getPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream()
                    .write(
                            ("GET "
                                            + path
                                            + " HTTP/1.1\r\n"
                                            + "Host: localhost\r\n"
                                            + "Connection: close\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
            try {
                String response =
                        new String(
                                socket.getInputStream().readNBytes(200), StandardCharsets.US_ASCII);
                if (response.contains("200 OK") || response.contains("303 See Other")) {
                    throw new IllegalStateException("Plain HTTP API is accessible");
                }
            } catch (java.io.IOException expected) {

            }
        }
    }

    static HttpResponse<String> get(String url, int status) throws Exception {
        URI uri = URI.create(url);
        if (!uri.getScheme().equals("https")) {
            throw new IllegalArgumentException("HTTPS required");
        }
        var result =
                client.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        if (result.statusCode() != status) {
            throw new IllegalStateException(
                    url + ": expected " + status + ", received " + result.statusCode());
        }
        System.out.println("PASS " + status + " " + url);
        return result;
    }
}
