package com.prueba.polizas.integration.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.prueba.polizas.config.PolizasProperties;
import com.prueba.polizas.exception.ProblemDetailFactory;
import com.prueba.polizas.model.enums.OperacionCore;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Adapter HTTP hacia el CORE (02e, RN-15): forma de la peticion y tolerancia a fallos. */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("CoreEdicionHttpAdapter (02e, RN-15)")
class CoreEdicionHttpAdapterTest {

    private static final String URL_MOCK_LOCAL = "http://localhost:8080/core-mock/evento";
    private static final CoreEventoPayload RENOVAR_1 =
            new CoreEventoPayload("ACTUALIZACION", 1L, OperacionCore.RENOVAR_POLIZA, null);

    private MockRestServiceServer server;

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    private static PolizasProperties propiedades(String url) {
        return new PolizasProperties(
                new PolizasProperties.Seguridad("123456", "x-api-key"),
                new PolizasProperties.Ipc(new BigDecimal("0.0510")),
                new PolizasProperties.Core(url, "/core-mock/evento", 2000));
    }

    private CoreEdicionHttpAdapter adapter(String url, MockEnvironment environment) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new CoreEdicionHttpAdapter(builder.build(), propiedades(url), environment);
    }

    private CoreEdicionHttpAdapter adapterMockLocal() {
        return adapter("", new MockEnvironment().withProperty("local.server.port", "8080"));
    }

    @Test
    @DisplayName("Envia POST /core-mock/evento con x-api-key, X-Correlation-Id del MDC y el body JSON literal")
    void debeEnviarPeticionConHeadersYBody(CapturedOutput salida) {
        CoreEdicionHttpAdapter adapter = adapterMockLocal();
        MDC.put(ProblemDetailFactory.MDC_CORRELATION_ID, "corr-adapter-1");
        server.expect(requestTo(URL_MOCK_LOCAL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "123456"))
                .andExpect(header("X-Correlation-Id", "corr-adapter-1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"evento\":\"ACTUALIZACION\",\"polizaId\":1,\"operacion\":\"RENOVAR_POLIZA\","
                                + "\"riesgoId\":null}", true))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"recibido\":true}"));

        adapter.notificar(RENOVAR_1);

        server.verify();
        assertThat(salida.getOut())
                .contains("[CORE-ADAPTER] Enviando evento al CORE polizaId=1, operacion=RENOVAR_POLIZA")
                .contains("[CORE-ADAPTER] Evento aceptado por el CORE polizaId=1, operacion=RENOVAR_POLIZA")
                .doesNotContain("Falló envío");
    }

    @Test
    @DisplayName("Envia riesgoId en el body cuando la operacion es sobre un riesgo")
    void debeEnviarRiesgoId() {
        CoreEdicionHttpAdapter adapter = adapterMockLocal();
        server.expect(requestTo(URL_MOCK_LOCAL))
                .andExpect(content().json(
                        "{\"evento\":\"ACTUALIZACION\",\"polizaId\":4,\"operacion\":\"CANCELAR_RIESGO\","
                                + "\"riesgoId\":21}", true))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        adapter.notificar(new CoreEventoPayload("ACTUALIZACION", 4L, OperacionCore.CANCELAR_RIESGO, 21L));

        server.verify();
    }

    @Test
    @DisplayName("Sin correlationId en el MDC no envia el header X-Correlation-Id")
    void noDebeEnviarCorrelationIdSiNoHayMdc() {
        CoreEdicionHttpAdapter adapter = adapterMockLocal();
        server.expect(requestTo(URL_MOCK_LOCAL))
                .andExpect(header("x-api-key", "123456"))
                .andExpect(headerDoesNotExist("X-Correlation-Id"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        adapter.notificar(RENOVAR_1);

        server.verify();
    }

    @Test
    @DisplayName("RN-15: un 500 del CORE se registra en ERROR [CORE-ADAPTER] y no se propaga")
    void noDebePropagarError500(CapturedOutput salida) {
        CoreEdicionHttpAdapter adapter = adapterMockLocal();
        server.expect(requestTo(URL_MOCK_LOCAL)).andRespond(withServerError());

        assertThatCode(() -> adapter.notificar(RENOVAR_1)).doesNotThrowAnyException();

        server.verify();
        assertThat(salida.getOut())
                .containsPattern("ERROR .*\\[CORE-ADAPTER] Falló envío al CORE polizaId=1 causa=.*500")
                .doesNotContain("Evento aceptado por el CORE");
    }

    @Test
    @DisplayName("RN-15: un IOException (CORE inalcanzable) se registra en ERROR y no se propaga")
    void noDebePropagarIoException(CapturedOutput salida) {
        CoreEdicionHttpAdapter adapter = adapterMockLocal();
        server.expect(requestTo(URL_MOCK_LOCAL)).andRespond(withException(new IOException("Connection refused")));

        assertThatCode(() -> adapter.notificar(RENOVAR_1)).doesNotThrowAnyException();

        assertThat(salida.getOut())
                .containsPattern("ERROR .*\\[CORE-ADAPTER] Falló envío al CORE polizaId=1 causa=.*Connection refused");
    }

    @Test
    @DisplayName("El log del adapter nunca contiene la API key")
    void noDebeRegistrarApiKey(CapturedOutput salida) {
        CoreEdicionHttpAdapter adapter = adapterMockLocal();
        server.expect(requestTo(URL_MOCK_LOCAL)).andRespond(withServerError());

        adapter.notificar(RENOVAR_1);

        assertThat(salida.getOut().lines().filter(l -> l.contains("[CORE-ADAPTER]")))
                .isNotEmpty()
                .noneMatch(l -> l.contains("123456"));
    }

    @Test
    @DisplayName("URL configurada: usa polizas.core.url (sin '/' final duplicado) + path")
    void debeUsarUrlConfigurada() {
        CoreEdicionHttpAdapter adapter = adapter("https://weblogic.core.local:7002/",
                new MockEnvironment().withProperty("local.server.port", "8080"));

        assertThat(adapter.urlDestino()).isEqualTo("https://weblogic.core.local:7002/core-mock/evento");
    }

    @Test
    @DisplayName("URL vacia: usa el puerto real del servidor (local.server.port)")
    void debeUsarPuertoLocalReal() {
        CoreEdicionHttpAdapter adapter = adapter("", new MockEnvironment().withProperty("local.server.port", "54321"));

        assertThat(adapter.urlDestino()).isEqualTo("http://localhost:54321/core-mock/evento");
    }

    @Test
    @DisplayName("URL vacia sin local.server.port (o 0): cae a server.port y luego a 8080")
    void debeCaerAServerPortY8080() {
        assertThat(adapter(null, new MockEnvironment().withProperty("local.server.port", "0")
                .withProperty("server.port", "9090")).urlDestino())
                .isEqualTo("http://localhost:9090/core-mock/evento");
        assertThat(adapter("  ", new MockEnvironment()).urlDestino())
                .isEqualTo("http://localhost:8080/core-mock/evento");
    }
}
