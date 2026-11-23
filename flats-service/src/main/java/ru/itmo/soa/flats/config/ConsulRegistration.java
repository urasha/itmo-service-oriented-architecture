package ru.itmo.soa.flats.config;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public final class ConsulRegistration implements SmartInitializingSingleton, DisposableBean {
    private final ConsulClient consul;
    private final URI endpoint;
    private final String id;

    public ConsulRegistration(@Value("${CONSUL_HOST:127.0.0.1}") String host,
            @Value("${CONSUL_HTTP_PORT:8500}") int port,
            @Value("${FLATS_INTERNAL_URL}") URI endpoint) {
        this.consul = new ConsulClient(host, port);
        this.endpoint = endpoint;
        this.id = "flats-" + endpoint.getHost() + "-" + endpoint.getPort();
    }

    @Override
    public void afterSingletonsInstantiated() {
        var check = new NewService.Check();
        check.setHttp(endpoint.resolve("/flats?size=1").toString());
        check.setInterval("5s");
        check.setTimeout("3s");

        var service = new NewService();
        service.setId(id);
        service.setName("flats");
        service.setAddress(endpoint.getHost());
        service.setPort(endpoint.getPort());
        service.setCheck(check);
        consul.agentServiceRegister(service);
    }

    @Override
    public void destroy() {
        consul.agentServiceDeregister(id);
    }
}
