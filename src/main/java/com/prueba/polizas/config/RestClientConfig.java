package com.prueba.polizas.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** {@link RestClient} para el CORE con timeouts de conexion y lectura = {@code polizas.core.timeout-ms}. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient coreRestClient(RestClient.Builder builder, PolizasProperties properties) {
        Duration timeout = Duration.ofMillis(properties.core().timeoutMs());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return builder.requestFactory(factory).build();
    }
}
