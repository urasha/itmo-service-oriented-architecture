package ru.itmo.soa.flats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.itmo.soa.contract.FlatOperations;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

@Configuration
public class BusinessConfig {
    @Bean(destroyMethod = "close")
    public InitialContext ejbContext(@Value("${EJB_PROVIDER_URL}") String providerUrl)
            throws NamingException {
        Properties properties = new Properties();
        properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        properties.put(Context.PROVIDER_URL, providerUrl);
        return new InitialContext(properties);
    }

    @Bean
    public FlatOperations flatOperations(InitialContext context) throws NamingException {
        String binding = "flats-ejb/FlatBusinessBean!" + FlatOperations.class.getName();
        return (FlatOperations) context.lookup(binding);
    }
}
