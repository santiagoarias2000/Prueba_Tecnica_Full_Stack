package com.prueba.polizas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.repository.PolizaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * E8 con el adapter real (sin spy): el CORE apunta a un puerto cerrado, la llamada HTTP falla con I/O,
 * el adapter lo registra en ERROR y la operacion responde 200 con el cambio persistido (RN-15).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:core-caido-${random.uuid}",
        "polizas.core.url=http://127.0.0.1:1",
        "polizas.core.timeout-ms=500"})
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Integracion CORE inalcanzable (02g E8; RN-15)")
class CoreInalcanzableIntegracionTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PolizaRepository polizaRepository;

    @Test
    @DisplayName("E8: con el CORE inalcanzable, renovar la poliza 1 responde 200, persiste y registra [CORE-ADAPTER] en ERROR")
    void coreInalcanzableNoAfectaLaRenovacion(CapturedOutput salida) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", "123456");
        headers.set("X-Correlation-Id", "e8-core-inalcanzable");

        ResponseEntity<JsonNode> respuesta = rest.exchange("/polizas/1/renovar", HttpMethod.POST,
                new HttpEntity<>(headers), JsonNode.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("estado").asText()).isEqualTo("RENOVADA");
        assertThat(respuesta.getBody().get("valorCanon").decimalValue()).isEqualByComparingTo("1576500.00");
        assertThat(polizaRepository.findById(1L)).hasValueSatisfying(p -> {
            assertThat(p.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
            assertThat(p.getValorPrima()).isEqualByComparingTo("18918000.00");
        });
        assertThat(salida.getOut())
                .containsPattern("ERROR +\\[e8-core-inalcanzable] .*\\[CORE-ADAPTER] Falló envío al CORE polizaId=1")
                .doesNotContain("[CORE-MOCK]")
                .doesNotContain("[CORE-LISTENER]");
    }
}
