package com.prueba.polizas.controller;

import static com.prueba.polizas.controller.Fixtures.API_KEY;
import static com.prueba.polizas.controller.Fixtures.API_KEY_HEADER;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.polizas.config.PolizasProperties;
import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.EstadoInvalidoException;
import com.prueba.polizas.exception.RecursoNoEncontradoException;
import com.prueba.polizas.exception.ReglaNegocioException;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.service.PolizaService;
import com.prueba.polizas.service.RiesgoService;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Casos C5 de 02g sobre {@code POST /riesgos/{id}/cancelar} (RF-06). */
@WebMvcTest(controllers = {PolizaController.class, RiesgoController.class})
@EnableConfigurationProperties(PolizasProperties.class)
@DisplayName("RiesgoController (02g seccion C, RF-06)")
class RiesgoControllerTest {

    private static final String TYPE_BASE = "https://api.polizas.local/errores/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiesgoService riesgoService;

    @MockitoBean
    private PolizaService polizaService;

    private ResultActions ejecutar(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(API_KEY_HEADER, API_KEY));
    }

    private static void esperarProblema(ResultActions acciones, int status, String title, String codigo,
                                        String instance) throws Exception {
        acciones.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + codigo))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(notNullValue()))
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()));
    }

    /** Riesgo 11 (1.200.000) de la poliza 2 ya cancelado en el dominio. */
    private static Riesgo riesgo11Cancelado(String motivo) {
        Poliza poliza = Fixtures.polizaColectiva2();
        Riesgo riesgo = poliza.getRiesgos().get(1);
        poliza.cancelarRiesgo(riesgo, motivo);
        return riesgo;
    }

    @Test
    @DisplayName("Responde 200 con el riesgo CANCELADO, fechaCancelacion = hoy y pasa el motivo al service")
    void debeCancelarRiesgoDeColectiva() throws Exception {
        when(riesgoService.cancelar(11L, "Terminación del contrato"))
                .thenReturn(riesgo11Cancelado("Terminación del contrato"));

        ejecutar(post("/riesgos/11/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Terminación del contrato\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.polizaId").value(2))
                .andExpect(jsonPath("$.valorCanon").value(1200000.00))
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.fechaCancelacion").value(LocalDate.now().toString()));

        verify(riesgoService).cancelar(11L, "Terminación del contrato");
    }

    @Test
    @DisplayName("Sin body cancela el riesgo con motivo null")
    void debeCancelarRiesgoSinBody() throws Exception {
        when(riesgoService.cancelar(eq(11L), isNull())).thenReturn(riesgo11Cancelado(null));

        ejecutar(post("/riesgos/11/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        verify(riesgoService).cancelar(eq(11L), isNull());
    }

    @Test
    @DisplayName("C5: riesgo inexistente responde 404 RIESGO_NO_ENCONTRADO")
    void debeResponder404CuandoRiesgoNoExiste() throws Exception {
        when(riesgoService.cancelar(eq(999L), isNull())).thenThrow(new RecursoNoEncontradoException(
                CodigoError.RIESGO_NO_ENCONTRADO, "No existe el riesgo 999"));

        esperarProblema(ejecutar(post("/riesgos/999/cancelar")), 404, "Recurso no encontrado",
                "RIESGO_NO_ENCONTRADO", "/riesgos/999/cancelar");
    }

    @Test
    @DisplayName("C5: riesgo ya cancelado responde 409 RIESGO_YA_CANCELADO")
    void debeRechazarCancelarRiesgoYaCancelado() throws Exception {
        when(riesgoService.cancelar(eq(22L), isNull())).thenThrow(new EstadoInvalidoException(
                CodigoError.RIESGO_YA_CANCELADO, "El riesgo 22 ya se encuentra cancelado"));

        esperarProblema(ejecutar(post("/riesgos/22/cancelar")), 409, "Estado inválido", "RIESGO_YA_CANCELADO",
                "/riesgos/22/cancelar");
    }

    @Test
    @DisplayName("C5: riesgo de poliza individual responde 422 RIESGO_INDIVIDUAL_NO_CANCELABLE")
    void debeRechazarCancelarRiesgoDeIndividual() throws Exception {
        when(riesgoService.cancelar(eq(1L), isNull())).thenThrow(new ReglaNegocioException(
                CodigoError.RIESGO_INDIVIDUAL_NO_CANCELABLE,
                "El riesgo de una poliza individual se cancela cancelando la poliza"));

        esperarProblema(ejecutar(post("/riesgos/1/cancelar")), 422, "Regla de negocio",
                "RIESGO_INDIVIDUAL_NO_CANCELABLE", "/riesgos/1/cancelar");
    }

    @Test
    @DisplayName("Motivo de mas de 300 caracteres responde 400 VALIDACION")
    void debeRechazarMotivoDemasiadoLargo() throws Exception {
        esperarProblema(ejecutar(post("/riesgos/11/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"" + "x".repeat(301) + "\"}")), 400, "Solicitud inválida",
                "VALIDACION", "/riesgos/11/cancelar");

        verifyNoInteractions(riesgoService);
    }
}
