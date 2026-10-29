package com.prueba.polizas.controller;

import static com.prueba.polizas.controller.Fixtures.API_KEY;
import static com.prueba.polizas.controller.Fixtures.API_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.polizas.config.PolizasProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Casos C6 y C7 de 02g sobre {@code POST /core-mock/evento} (RF-07). */
@WebMvcTest(controllers = CoreMockController.class)
@EnableConfigurationProperties(PolizasProperties.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("CoreMockController (02g C6, C7; RF-07)")
class CoreMockControllerTest {

    private static final String RUTA = "/core-mock/evento";
    private static final String TYPE_BASE = "https://api.polizas.local/errores/";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions enviar(String body) throws Exception {
        return mockMvc.perform(post(RUTA).header(API_KEY_HEADER, API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void esperarValidacion(ResultActions acciones, String campo) throws Exception {
        acciones.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "VALIDACION"))
                .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.instance").value(RUTA))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem(campo)));
    }

    @Test
    @DisplayName("C6: evento valido responde 202 {recibido, mensaje, timestamp} y registra [CORE-MOCK] polizaId=555")
    void debeAceptarEventoValidoYRegistrarloEnLog(CapturedOutput salida) throws Exception {
        enviar("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}")
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recibido").value(true))
                .andExpect(jsonPath("$.mensaje").value("Evento registrado en logs"))
                .andExpect(jsonPath("$.timestamp")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}-05:00")));

        assertThat(salida.getOut())
                .contains("[CORE-MOCK]")
                .contains("evento=ACTUALIZACION")
                .contains("polizaId=555");
    }

    @Test
    @DisplayName("C6: operacion y riesgoId opcionales se aceptan (202) y aparecen en el log junto al correlationId")
    void debeRegistrarOperacionYRiesgoOpcionales(CapturedOutput salida) throws Exception {
        mockMvc.perform(post(RUTA).header(API_KEY_HEADER, API_KEY).header("X-Correlation-Id", "corr-c6-opc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":2,"
                                + "\"operacion\":\"CANCELAR_RIESGO\",\"riesgoId\":11}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recibido").value(true));

        assertThat(salida.getOut())
                .contains("[CORE-MOCK] Intento de envío al CORE -> evento=ACTUALIZACION, polizaId=2, "
                        + "operacion=CANCELAR_RIESGO, riesgoId=11, correlationId=corr-c6-opc");
        assertThat(salida.getOut().lines().filter(l -> l.contains("[CORE-MOCK]")))
                .noneMatch(l -> l.contains(API_KEY));
    }

    @Test
    @DisplayName("C7: sin polizaId responde 400 VALIDACION con error en el campo polizaId y no registra [CORE-MOCK]")
    void debeRechazarEventoSinPolizaId(CapturedOutput salida) throws Exception {
        esperarValidacion(enviar("{\"evento\":\"ACTUALIZACION\"}"), "polizaId");

        assertThat(salida.getOut()).doesNotContain("[CORE-MOCK]");
    }

    @Test
    @DisplayName("C7: sin evento responde 400 VALIDACION con error en el campo evento")
    void debeRechazarEventoSinEvento() throws Exception {
        esperarValidacion(enviar("{\"polizaId\":555}"), "evento");
    }

    @Test
    @DisplayName("C7: evento en blanco responde 400 VALIDACION con error en el campo evento")
    void debeRechazarEventoEnBlanco() throws Exception {
        esperarValidacion(enviar("{\"evento\":\"   \",\"polizaId\":555}"), "evento");
    }

    @Test
    @DisplayName("C7: body vacio {} responde 400 con errores en evento y polizaId")
    void debeRechazarBodyVacio() throws Exception {
        enviar("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("evento")))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("polizaId")));
    }

    @Test
    @DisplayName("Sin x-api-key responde 401 API_KEY_INVALIDA y no registra [CORE-MOCK]")
    void debeRechazarSinApiKey(CapturedOutput salida) throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"))
                .andExpect(jsonPath("$.instance").value(RUTA));

        assertThat(salida.getOut()).doesNotContain("[CORE-MOCK]");
    }

    @Test
    @DisplayName("Con x-api-key incorrecta responde 401 API_KEY_INVALIDA")
    void debeRechazarApiKeyIncorrecta() throws Exception {
        mockMvc.perform(post(RUTA).header(API_KEY_HEADER, "000000").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"));
    }
}
