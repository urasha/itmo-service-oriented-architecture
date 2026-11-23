package ru.itmo.soa.agency;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.ConsulRawClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import ru.itmo.soa.agency.api.AgencyHttpFilter;
import ru.itmo.soa.agency.api.AgencyResource;
import ru.itmo.soa.agency.client.FlatsClient;
import ru.itmo.soa.agency.client.FlatsDiscovery;
import ru.itmo.soa.agency.config.AgencySettings;
import ru.itmo.soa.agency.exception.AgencyErrorMapper;
import ru.itmo.soa.agency.service.AgencyService;
import ru.itmo.soa.contract.JsonCodec;

@SpringBootApplication
@EnableConfigurationProperties(AgencySettings.class)
public class AgencyApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgencyApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return JsonCodec.createMapper();
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient consulHttpClient(AgencySettings settings) {
        int connect = Math.toIntExact(settings.connectTimeout().toMillis());
        int read = Math.toIntExact(settings.requestTimeout().toMillis());
        var timeouts = RequestConfig.custom().setConnectTimeout(connect)
                .setConnectionRequestTimeout(connect).setSocketTimeout(read).build();
        return HttpClients.custom().setDefaultRequestConfig(timeouts).disableAutomaticRetries().build();
    }

    @Bean
    public AgencyService agencyService(AgencySettings settings, ObjectMapper mapper,
            SslBundles bundles, CloseableHttpClient consulHttpClient,
            @Value("${CONSUL_HOST:127.0.0.1}") String host,
            @Value("${CONSUL_HTTP_PORT:8500}") int port) {
        var consul = new ConsulClient(new ConsulRawClient(host, port, consulHttpClient));
        var discovery = new FlatsDiscovery(consul);
        var client = new FlatsClient(settings, mapper, discovery, bundles.getBundle("flats").createSslContext());
        return new AgencyService(client);
    }

    @Bean
    public ResourceConfig jersey(AgencySettings settings, ObjectMapper mapper) {
        var errors = new AgencyErrorMapper(mapper);
        return new ResourceConfig()
                .property(ServerProperties.WADL_FEATURE_DISABLE, true)
                .register(AgencyResource.class)
                .registerInstances(new AgencyHttpFilter(settings.allowedOrigin(), errors), errors);
    }
}
