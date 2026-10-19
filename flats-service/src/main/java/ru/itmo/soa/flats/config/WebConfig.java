package ru.itmo.soa.flats.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.*;
import ru.itmo.soa.contract.JsonCodec;

@Configuration
@EnableWebMvc
@ComponentScan("ru.itmo.soa.flats")
public class WebConfig implements WebMvcConfigurer {
    @Bean public ObjectMapper objectMapper() { return JsonCodec.createMapper(); }
    @Bean public Clock clock() { return Clock.system(ZoneId.of("Europe/Moscow")); }
    @Override public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8));
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper()));
    }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/docs/**").addResourceLocations("/docs/");
        registry.addResourceHandler("/app/**").addResourceLocations("/app/");
    }
}
