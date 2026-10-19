import java.net.URI;
import java.net.Socket;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import javax.net.ssl.*;

/** Read-only probe for two real containers. JDK only, no curl or Maven on the host. */
class Smoke {
    static HttpClient client;
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("flatsInternalBase agencyInternalBase flatsCertificate agencyCertificate flatsPublicBase");
        KeyStore trust = KeyStore.getInstance("PKCS12"); trust.load(null, null);
        for (int i = 2; i <= 3; i++) try (var in = Files.newInputStream(Path.of(args[i]))) {
            trust.setCertificateEntry("service" + i, CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(trust);
        SSLContext ssl = SSLContext.getInstance("TLS"); ssl.init(null, managers.getTrustManagers(), null);
        client = HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        get(args[0] + "/flats", 200);
        var app = get(args[0] + "/app/index.html", 200);
        if (!app.body().contains("./app.js")) throw new IllegalStateException("Client HTML is missing");
        get(args[0] + "/app/config.js", 200); get(args[0] + "/docs/index.html", 200);
        var redirect = get(args[1] + "/agency/get-cheapest/1/1", 303);
        if (!redirect.headers().firstValue("Location").orElse("").equals(args[4].replaceAll("/+$", "") + "/flats/1"))
            throw new IllegalStateException("Incorrect public Location");
        get(args[1] + "/agency/get-cheapest/9223372036854775807/9223372036854775806", 404);
        for (int i = 0; i <= 1; i++) {
            URI base = URI.create(args[i]);
            try (Socket socket = new Socket(base.getHost(), base.getPort() == -1 ? 443 : base.getPort())) {
                socket.setSoTimeout(3000);
                socket.getOutputStream().write(("GET " + (i == 0 ? "/flats" : "/agency/get-cheapest/1/1") + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                try {
                    String response = new String(socket.getInputStream().readNBytes(200), StandardCharsets.US_ASCII);
                    if (response.contains("200 OK") || response.contains("303 See Other")) throw new IllegalStateException("Plain HTTP API is accessible");
                } catch (java.io.IOException expected) { /* TLS listener rejected plaintext. */ }
            }
        }
        System.out.println("PASS HTTPS, client, Swagger, agency redirect/upstream and plaintext rejection. Browser CORS and Helios OS compatibility still require target verification.");
    }
    static HttpResponse<String> get(String url, int status) throws Exception {
        URI uri = URI.create(url);
        if (!uri.getScheme().equals("https")) throw new IllegalArgumentException("HTTPS required");
        var result = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (result.statusCode() != status) throw new IllegalStateException(url + ": expected " + status + ", received " + result.statusCode());
        System.out.println("PASS " + status + " " + url); return result;
    }
}
