package ru.itmo.soa.agency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.itmo.soa.contract.Flat;
import ru.itmo.soa.contract.FlatValidator;
import ru.itmo.soa.contract.JsonCodec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** HTTPS only; the trust configuration is private to this client, not the Payara JVM. */
public final class FlatsClient {
    private final HttpClient client;
    private final AgencySettings settings;
    private final ObjectMapper mapper;

    public FlatsClient(AgencySettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        try {
            char[] password = Files.readString(settings.trustStorePasswordFile(), StandardCharsets.UTF_8).trim().toCharArray();
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(settings.trustStore())) { store.load(input, password); }
            finally { Arrays.fill(password, '\0'); }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            SSLParameters parameters = new SSLParameters();
            parameters.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            client = HttpClient.newBuilder().sslContext(context).sslParameters(parameters)
                    .connectTimeout(settings.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER)
                    .version(HttpClient.Version.HTTP_1_1).build();
        } catch (Exception e) { throw new IllegalStateException("Cannot initialize HTTPS client truststore", e); }
    }

    public Flat get(long id) {
        HttpRequest request = HttpRequest.newBuilder(AgencySettings.flatUri(settings.internalBaseUrl(), id))
                .timeout(settings.requestTimeout()).header("Accept", "application/json").GET().build();
        CompletableFuture<HttpResponse<String>> pending = client.sendAsync(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> response;
        try { response = pending.get(settings.requestTimeout().toMillis(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { pending.cancel(true); throw AgencyException.timeout(); }
        catch (InterruptedException e) { pending.cancel(true); Thread.currentThread().interrupt(); throw AgencyException.upstream(); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException) throw AgencyException.timeout();
            throw AgencyException.upstream(e.getCause());
        }
        if (response.statusCode() == 404) throw new AgencyException(404, "Квартира не найдена");
        if (response.statusCode() != 200) throw AgencyException.upstream();
        String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
        if (!type.equalsIgnoreCase("application/json")) throw AgencyException.upstream();
        try {
            var node = JsonCodec.parse(mapper, response.body());
            FlatValidator.validate(node, FlatValidator.Mode.FILTER);
            if (!node.has("id") || !node.has("creationDate") || !node.has("transport")) throw AgencyException.upstream();
            ObjectNode input = ((ObjectNode) node).deepCopy();
            input.remove(java.util.List.of("id", "creationDate"));
            FlatValidator.validate(input, FlatValidator.Mode.INPUT);
            Flat flat = mapper.treeToValue(node, Flat.class);
            if (flat.id() != id) throw AgencyException.upstream();
            return flat;
        } catch (Exception e) { throw AgencyException.upstream(e); }
    }
}
