package ru.itmo.soa.agency.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.itmo.soa.agency.config.AgencySettings;
import ru.itmo.soa.agency.exception.AgencyException;
import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.FlatValidator;
import ru.itmo.soa.contract.JsonCodec;
import ru.itmo.soa.contract.ValidationException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

public final class FlatsClient {
    private final HttpClient client;
    private final AgencySettings settings;
    private final ObjectMapper mapper;

    public FlatsClient(AgencySettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        this.client = createClient(settings);
    }

    private static HttpClient createClient(AgencySettings settings) {
        return HttpClient.newBuilder()
                .sslContext(createSslContext(settings))
                .sslParameters(sslParameters())
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private static SSLParameters sslParameters() {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
    }

    private static SSLContext createSslContext(AgencySettings settings) {
        try {
            TrustManagerFactory factory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(loadTrustStore(settings));
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot initialize HTTPS client truststore", e);
        }
    }

    public Flat get(long id) {
        HttpResponse<String> response = send(flatRequest(id));
        validateResponse(response);
        return readFlat(response.body(), id);
    }

    private static KeyStore loadTrustStore(AgencySettings settings)
            throws IOException, GeneralSecurityException {
        char[] password =
                Files.readString(settings.trustStorePasswordFile(), StandardCharsets.UTF_8)
                        .trim()
                        .toCharArray();
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(settings.trustStore())) {
                store.load(input, password);
            }
            return store;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private HttpRequest flatRequest(long id) {
        return HttpRequest.newBuilder(AgencySettings.flatUri(settings.internalBaseUrl(), id))
                .timeout(settings.requestTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {

        CompletableFuture<HttpResponse<String>> pending =
                client.sendAsync(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return pending.get(settings.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw AgencyException.timeout();
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw AgencyException.upstream();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                throw AgencyException.timeout();
            }
            throw AgencyException.upstream(e.getCause());
        }
    }

    private void validateResponse(HttpResponse<String> response) {
        if (response.statusCode() == 404) {
            throw new AgencyException(404, "Квартира не найдена");
        }
        if (response.statusCode() != 200) {
            throw AgencyException.upstream();
        }
        String type =
                response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
        if (!type.equalsIgnoreCase("application/json")) {
            throw AgencyException.upstream();
        }
    }

    private Flat readFlat(String body, long expectedId) {
        try {
            var node = JsonCodec.parse(mapper, body);
            validateFlat(node);
            Flat flat = mapper.treeToValue(node, Flat.class);
            if (flat.id() != expectedId) {
                throw AgencyException.upstream();
            }
            return flat;
        } catch (JsonProcessingException | ValidationException | IllegalArgumentException e) {
            throw AgencyException.upstream(e);
        }
    }

    private void validateFlat(JsonNode node) {
        FlatValidator.validate(node, FlatValidator.Mode.FILTER);
        if (!node.has("id") || !node.has("creationDate") || !node.has("transport")) {
            throw AgencyException.upstream();
        }
        ObjectNode input = ((ObjectNode) node).deepCopy();
        input.remove(List.of("id", "creationDate"));
        FlatValidator.validate(input, FlatValidator.Mode.INPUT);
    }
}
