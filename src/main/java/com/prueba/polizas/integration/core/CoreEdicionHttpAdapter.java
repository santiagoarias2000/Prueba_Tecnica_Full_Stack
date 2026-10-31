package com.prueba.polizas.integration.core;

import com.prueba.polizas.config.CorrelationIdFilter;
import com.prueba.polizas.config.PolizasProperties;
import com.prueba.polizas.exception.ProblemDetailFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Adapter HTTP del {@link CoreEdicionPort}: {@code POST {polizas.core.url}{polizas.core.path}} con los headers
 * de API key y {@code X-Correlation-Id}. Nunca relanza: un fallo del CORE solo se registra.
 *
 * <p>Si {@code polizas.core.url} esta vacia, apunta al mock local resolviendo el puerto real del servidor
 * en cada envio ({@code local.server.port}), lo que funciona tanto con {@code server.port=8080} como con
 * puertos aleatorios en tests.
 */
@Component
public class CoreEdicionHttpAdapter implements CoreEdicionPort {

    private static final Logger log = LoggerFactory.getLogger(CoreEdicionHttpAdapter.class);

    private final RestClient restClient;
    private final PolizasProperties properties;
    private final Environment environment;

    public CoreEdicionHttpAdapter(RestClient coreRestClient, PolizasProperties properties, Environment environment) {
        this.restClient = coreRestClient;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void notificar(CoreEventoPayload payload) {
        log.info("[CORE-ADAPTER] Enviando evento al CORE polizaId={}, operacion={}",
                payload.polizaId(), payload.operacion());
        try {
            String correlationId = MDC.get(ProblemDetailFactory.MDC_CORRELATION_ID);
            PolizasProperties.Seguridad seguridad = properties.seguridad();
            restClient.post()
                    .uri(urlDestino())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(seguridad.header(), seguridad.apiKey())
                    .headers(h -> {
                        if (correlationId != null) {
                            h.set(CorrelationIdFilter.HEADER, correlationId);
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[CORE-ADAPTER] Evento aceptado por el CORE polizaId={}, operacion={}",
                    payload.polizaId(), payload.operacion());
        } catch (RuntimeException e) {
            log.error("[CORE-ADAPTER] Falló envío al CORE polizaId={} causa={}", payload.polizaId(), e.toString());
        }
    }

    String urlDestino() {
        PolizasProperties.Core core = properties.core();
        String base = StringUtils.hasText(core.url()) ? core.url() : "http://localhost:" + puertoLocal();
        return base.replaceAll("/+$", "") + core.path();
    }

    private String puertoLocal() {
        String puerto = environment.getProperty("local.server.port");
        if (!StringUtils.hasText(puerto) || "0".equals(puerto)) {
            puerto = environment.getProperty("server.port", "8080");
        }
        return puerto;
    }
}
