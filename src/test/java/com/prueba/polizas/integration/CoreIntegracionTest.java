package com.prueba.polizas.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.prueba.polizas.integration.core.CoreEdicionPort;
import com.prueba.polizas.integration.core.CoreEventoPayload;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.OperacionCore;
import com.prueba.polizas.repository.PolizaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Casos E7 y E8 de 02g: integracion con el CORE de punta a punta (servidor real en puerto aleatorio).
 * El spy envuelve al adapter real, de modo que tambien se ejerce el flujo HTTP real hacia {@code /core-mock/evento}.
 * Cada test usa una base H2 propia y un contexto nuevo ({@link DirtiesContext}) para no depender del orden.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:core-it-${random.uuid}")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Integracion CORE (02g E7, E8; RN-15)")
class CoreIntegracionTest {

    private static final String API_KEY = "123456";

    @Autowired
    private TestRestTemplate rest;

    @MockitoSpyBean
    private CoreEdicionPort coreEdicionPort;

    @Autowired
    private PolizaRepository polizaRepository;

    private ResponseEntity<JsonNode> post(String ruta, String body, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", API_KEY);
        headers.set("X-Correlation-Id", correlationId);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(ruta, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String ruta) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", API_KEY);
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private CoreEventoPayload unicoPayloadNotificado() {
        ArgumentCaptor<CoreEventoPayload> captor = ArgumentCaptor.forClass(CoreEventoPayload.class);
        verify(coreEdicionPort, times(1)).notificar(captor.capture());
        return captor.getValue();
    }

    private static List<String> lineas(CapturedOutput salida, String marcador) {
        return salida.getOut().lines().filter(l -> l.contains(marcador)).toList();
    }

    /** Verifica la pareja adapter -> mock en el log, con el mismo correlationId y sin la API key. */
    private static void esperarParejaEnLog(CapturedOutput salida, String correlationId, String polizaId,
                                           String operacion, String riesgoId) {
        assertThat(lineas(salida, "[CORE-ADAPTER] Enviando evento"))
                .singleElement().asString()
                .contains("polizaId=" + polizaId + ", operacion=" + operacion)
                .contains("[" + correlationId + "]");
        assertThat(lineas(salida, "[CORE-MOCK]"))
                .singleElement().asString()
                .contains("evento=ACTUALIZACION, polizaId=" + polizaId + ", operacion=" + operacion
                        + ", riesgoId=" + riesgoId + ", correlationId=" + correlationId);
        assertThat(lineas(salida, "[CORE-ADAPTER] Evento aceptado")).hasSize(1);
        assertThat(lineas(salida, "[CORE-")).noneMatch(l -> l.contains(API_KEY));
        assertThat(salida.getOut()).doesNotContain("Falló envío");
    }

    @Test
    @DisplayName("E7: renovar la poliza 1 notifica 1 vez RENOVAR_POLIZA (polizaId=1, riesgoId=null) y llega al mock")
    void renovarNotificaUnaVez(CapturedOutput salida) {
        ResponseEntity<JsonNode> respuesta = post("/polizas/1/renovar", null, "e7-renovar");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("estado").asText()).isEqualTo("RENOVADA");
        assertThat(respuesta.getBody().get("valorCanon").decimalValue()).isEqualByComparingTo("1576500.00");
        assertThat(respuesta.getBody().get("valorPrima").decimalValue()).isEqualByComparingTo("18918000.00");
        assertThat(unicoPayloadNotificado())
                .isEqualTo(new CoreEventoPayload("ACTUALIZACION", 1L, OperacionCore.RENOVAR_POLIZA, null));
        esperarParejaEnLog(salida, "e7-renovar", "1", "RENOVAR_POLIZA", "null");
    }

    @Test
    @DisplayName("E7: cancelar la poliza 2 notifica 1 vez CANCELAR_POLIZA (polizaId=2, riesgoId=null)")
    void cancelarPolizaNotificaUnaVez(CapturedOutput salida) {
        ResponseEntity<JsonNode> respuesta = post("/polizas/2/cancelar",
                "{\"motivo\":\"Solicitud del tomador\"}", "e7-cancelar-poliza");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("estado").asText()).isEqualTo("CANCELADA");
        assertThat(respuesta.getBody().get("riesgosCancelados").asInt()).isEqualTo(2);
        assertThat(unicoPayloadNotificado())
                .isEqualTo(new CoreEventoPayload("ACTUALIZACION", 2L, OperacionCore.CANCELAR_POLIZA, null));
        esperarParejaEnLog(salida, "e7-cancelar-poliza", "2", "CANCELAR_POLIZA", "null");
    }

    @Test
    @DisplayName("E7: agregar riesgo a la poliza 2 notifica 1 vez AGREGAR_RIESGO con el id del riesgo creado")
    void agregarRiesgoNotificaUnaVez(CapturedOutput salida) {
        ResponseEntity<JsonNode> respuesta = post("/polizas/2/riesgos",
                "{\"descripcion\":\"Apto 502 - Calle 100 # 10-20, Bogotá\",\"arrendatario\":\"Luis Martínez\","
                        + "\"arrendador\":\"María Rodríguez\",\"valorCanon\":1800000.00}", "e7-agregar-riesgo");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long riesgoId = respuesta.getBody().get("id").asLong();
        assertThat(respuesta.getHeaders().getLocation()).hasToString("/riesgos/" + riesgoId);
        assertThat(unicoPayloadNotificado())
                .isEqualTo(new CoreEventoPayload("ACTUALIZACION", 2L, OperacionCore.AGREGAR_RIESGO, riesgoId));
        esperarParejaEnLog(salida, "e7-agregar-riesgo", "2", "AGREGAR_RIESGO", String.valueOf(riesgoId));
        // RN-12: 2.000.000 + 1.200.000 + 1.800.000 = 5.000.000 x 6 meses
        assertThat(polizaRepository.findById(2L)).hasValueSatisfying(p -> {
            assertThat(p.getValorCanon()).isEqualByComparingTo("5000000.00");
            assertThat(p.getValorPrima()).isEqualByComparingTo("30000000.00");
        });
    }

    @Test
    @DisplayName("E7: cancelar el riesgo 21 (poliza 4) notifica 1 vez CANCELAR_RIESGO (polizaId=4, riesgoId=21)")
    void cancelarRiesgoNotificaUnaVez(CapturedOutput salida) {
        ResponseEntity<JsonNode> respuesta = post("/riesgos/21/cancelar",
                "{\"motivo\":\"Terminación del contrato\"}", "e7-cancelar-riesgo");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("estado").asText()).isEqualTo("CANCELADO");
        assertThat(unicoPayloadNotificado())
                .isEqualTo(new CoreEventoPayload("ACTUALIZACION", 4L, OperacionCore.CANCELAR_RIESGO, 21L));
        esperarParejaEnLog(salida, "e7-cancelar-riesgo", "4", "CANCELAR_RIESGO", "21");
    }

    @Test
    @DisplayName("E7: renovar la poliza 3 (CANCELADA) responde 409 y no notifica al CORE")
    void operacionFallidaPorReglaNoNotifica(CapturedOutput salida) {
        ResponseEntity<JsonNode> respuesta = post("/polizas/3/renovar", null, "e7-409");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("POLIZA_CANCELADA_NO_RENOVABLE");
        verify(coreEdicionPort, never()).notificar(any());
        assertThat(lineas(salida, "[CORE-")).isEmpty();
    }

    @Test
    @DisplayName("E7: agregar riesgo a la poliza individual 1 responde 422 y no notifica al CORE")
    void operacionRechazada422NoNotifica(CapturedOutput salida) {
        ResponseEntity<JsonNode> respuesta = post("/polizas/1/riesgos",
                "{\"descripcion\":\"Local 1\",\"arrendatario\":\"A\",\"arrendador\":\"B\",\"valorCanon\":1000000}",
                "e7-422");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("TIPO_POLIZA_NO_PERMITE_RIESGOS");
        verify(coreEdicionPort, never()).notificar(any());
        assertThat(lineas(salida, "[CORE-")).isEmpty();
    }

    @Test
    @DisplayName("E8: si el puerto del CORE lanza RuntimeException, renovar responde 200 y el cambio persiste")
    void falloDelCoreNoAfectaLaOperacion(CapturedOutput salida) {
        doThrow(new IllegalStateException("CORE caido")).when(coreEdicionPort).notificar(any());

        ResponseEntity<JsonNode> respuesta = post("/polizas/1/renovar", null, "e8-core-caido");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("estado").asText()).isEqualTo("RENOVADA");
        verify(coreEdicionPort, times(1)).notificar(any());
        assertThat(salida.getOut()).contains("[CORE-LISTENER]").contains("CORE caido");

        assertThat(polizaRepository.findById(1L)).hasValueSatisfying(p -> {
            assertThat(p.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
            assertThat(p.getValorCanon()).isEqualByComparingTo(new BigDecimal("1576500.00"));
            assertThat(p.getValorPrima()).isEqualByComparingTo(new BigDecimal("18918000.00"));
        });
        ResponseEntity<JsonNode> renovadas = get("/polizas?estado=RENOVADA");
        assertThat(renovadas.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(StreamSupport.stream(renovadas.getBody().spliterator(), false).map(n -> n.get("id").asLong()))
                .contains(1L);
    }
}
