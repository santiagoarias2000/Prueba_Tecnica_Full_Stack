package com.prueba.polizas.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.polizas.integration.core.CoreEdicionPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Casos E1-E6 de 02g: flujos end-to-end sobre los datos semilla (data.sql) a traves de toda la pila
 * (filtros, controller, servicio, JPA, H2). Cada test usa una base H2 propia y un contexto nuevo
 * ({@link DirtiesContext}) para no depender del orden. El puerto del CORE se sustituye por un mock
 * porque su comportamiento ya se cubre en {@link CoreIntegracionTest} (E7, E8).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:it-${random.uuid}")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Integracion end-to-end sobre datos semilla (02g E1-E6)")
class PolizasApiIntegrationTest {

    private static final String API_KEY_HEADER = "x-api-key";
    private static final String API_KEY = "123456";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CoreEdicionPort coreEdicionPort;

    private ResultActions ejecutar(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(API_KEY_HEADER, API_KEY));
    }

    private ResultActions postJson(String ruta, String body) throws Exception {
        return ejecutar(post(ruta).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("E1: renovar la poliza 1 la deja RENOVADA con canon 1.576.500, prima 18.918.000 y vigencia 2027")
    void renovarPoliza1ApareceEnListadoDeRenovadas() throws Exception {
        ejecutar(post("/polizas/1/renovar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RENOVADA"));

        JsonNode renovadas = leer(ejecutar(get("/polizas").param("estado", "RENOVADA"))
                .andExpect(status().isOk()));
        JsonNode poliza1 = null;
        for (JsonNode p : renovadas) {
            assertThat(p.get("estado").asText()).isEqualTo("RENOVADA");
            if (p.get("id").asLong() == 1L) {
                poliza1 = p;
            }
        }
        assertThat(poliza1).as("la poliza 1 debe estar en el listado de RENOVADA").isNotNull();
        assertThat(poliza1.get("valorCanon").decimalValue()).isEqualByComparingTo("1576500.00");
        assertThat(poliza1.get("valorPrima").decimalValue()).isEqualByComparingTo("18918000.00");
        assertThat(poliza1.get("fechaInicioVigencia").asText()).isEqualTo("2027-01-01");
        assertThat(poliza1.get("fechaFinVigencia").asText()).isEqualTo("2027-12-31");
        assertThat(poliza1.get("mesesVigencia").asInt()).isEqualTo(12);
        // La poliza 4 (semilla RENOVADA) tambien debe seguir apareciendo.
        assertThat(renovadas.findValuesAsText("id")).contains("1", "4");
    }

    @Test
    @DisplayName("E2: renovar la poliza 3 (CANCELADA) responde 409 POLIZA_CANCELADA_NO_RENOVABLE y no cambia")
    void renovarPolizaCanceladaResponde409() throws Exception {
        ejecutar(post("/polizas/3/renovar"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value("POLIZA_CANCELADA_NO_RENOVABLE"));

        ejecutar(get("/polizas/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.valorCanon").value(950000.00))
                .andExpect(jsonPath("$.valorPrima").value(11400000.00))
                .andExpect(jsonPath("$.fechaInicioVigencia").value("2025-06-01"))
                .andExpect(jsonPath("$.fechaFinVigencia").value("2026-05-31"));
    }

    @Test
    @DisplayName("E3: agregar riesgo a la poliza 2 responde 201 + Location; quedan 3 riesgos y canon/prima recalculados")
    void agregarRiesgoAPolizaColectiva() throws Exception {
        JsonNode creado = leer(postJson("/polizas/2/riesgos",
                "{\"descripcion\":\"Apto 703 - Cra 15 # 93-20, Bogotá\",\"arrendatario\":\"Paula Ríos\","
                        + "\"arrendador\":\"Inversiones Andes SAS\",\"valorCanon\":1800000.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.polizaId").value(2))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.valorCanon").value(1800000.00)));
        long riesgoId = creado.get("id").asLong();
        assertThat(riesgoId).isPositive();

        ejecutar(get("/polizas/2/riesgos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", hasItem((int) riesgoId)))
                .andExpect(jsonPath("$[*].estado", not(hasItem("CANCELADO"))));

        // RN-12: 2.000.000 + 1.200.000 + 1.800.000 = 5.000.000; prima = canon x 6 meses
        JsonNode poliza = leer(ejecutar(get("/polizas/2")).andExpect(status().isOk()));
        assertThat(poliza.get("valorCanon").decimalValue()).isEqualByComparingTo("5000000.00");
        assertThat(poliza.get("valorPrima").decimalValue())
                .isEqualByComparingTo(new BigDecimal("5000000.00").multiply(BigDecimal.valueOf(6)));
        assertThat(poliza.get("cantidadRiesgosActivos").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("E3: el header Location del 201 apunta a /riesgos/{idCreado}")
    void agregarRiesgoDevuelveLocation() throws Exception {
        ResultActions resultado = postJson("/polizas/2/riesgos",
                "{\"descripcion\":\"Local 1\",\"arrendatario\":\"A\",\"arrendador\":\"B\",\"valorCanon\":1000000}")
                .andExpect(status().isCreated());
        long riesgoId = leer(resultado).get("id").asLong();
        resultado.andExpect(header().string("Location", "/riesgos/" + riesgoId));
    }

    @Test
    @DisplayName("E4: agregar riesgo a la poliza individual 1 responde 422 TIPO_POLIZA_NO_PERMITE_RIESGOS y sigue con 1 riesgo")
    void agregarRiesgoAPolizaIndividualResponde422() throws Exception {
        postJson("/polizas/1/riesgos",
                "{\"descripcion\":\"Local 1\",\"arrendatario\":\"A\",\"arrendador\":\"B\",\"valorCanon\":1000000}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.codigo").value("TIPO_POLIZA_NO_PERMITE_RIESGOS"));

        ejecutar(get("/polizas/1/riesgos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1));
        ejecutar(get("/polizas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorCanon").value(1500000.00))
                .andExpect(jsonPath("$.valorPrima").value(18000000.00));
    }

    @Test
    @DisplayName("E5: cancelar la poliza 2 responde 200 CANCELADA con riesgosCancelados=2 y todos sus riesgos CANCELADO")
    void cancelarPolizaCancelaTodosSusRiesgos() throws Exception {
        LocalDate antes = LocalDate.now();
        postJson("/polizas/2/cancelar", "{\"motivo\":\"Solicitud del tomador\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.riesgosCancelados").value(2));
        LocalDate despues = LocalDate.now();

        JsonNode riesgos = leer(ejecutar(get("/polizas/2/riesgos")).andExpect(status().isOk()));
        assertThat(riesgos).hasSize(2);
        for (JsonNode r : riesgos) {
            assertThat(r.get("estado").asText()).isEqualTo("CANCELADO");
            assertThat(r.get("fechaCancelacion").isNull()).as("fechaCancelacion del riesgo %s", r.get("id")).isFalse();
            assertThat(LocalDate.parse(r.get("fechaCancelacion").asText())).isBetween(antes, despues);
        }
        ejecutar(get("/polizas/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.cantidadRiesgosActivos").value(0));
    }

    @Test
    @DisplayName("E6: cancelar el riesgo 21 de la poliza 4 responde 200 CANCELADO; canon = 1.800.000 y prima = canon x 12")
    void cancelarRiesgoRecalculaCanonDeLaPoliza() throws Exception {
        ejecutar(get("/polizas/4"))
                .andExpect(jsonPath("$.cantidadRiesgosActivos").value(2))
                .andExpect(jsonPath("$.valorCanon").value(3150000.00));

        postJson("/riesgos/21/cancelar", "{\"motivo\":\"Terminación del contrato\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.polizaId").value(4))
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.fechaCancelacion").isNotEmpty());

        // RN-12: solo queda activo el riesgo 20 (1.800.000); prima = 1.800.000 x 12 meses
        JsonNode poliza = leer(ejecutar(get("/polizas/4")).andExpect(status().isOk()));
        assertThat(poliza.get("estado").asText()).isEqualTo("RENOVADA");
        assertThat(poliza.get("valorCanon").decimalValue()).isEqualByComparingTo("1800000.00");
        assertThat(poliza.get("valorPrima").decimalValue()).isEqualByComparingTo("21600000.00");
        assertThat(poliza.get("cantidadRiesgosActivos").asLong()).isEqualTo(1);

        JsonNode colectivas = leer(ejecutar(get("/polizas").param("tipo", "COLECTIVA")).andExpect(status().isOk()));
        JsonNode enListado = null;
        for (JsonNode p : colectivas) {
            if (p.get("id").asLong() == 4L) {
                enListado = p;
            }
        }
        assertThat(enListado).isNotNull();
        assertThat(enListado.get("valorCanon").decimalValue()).isEqualByComparingTo("1800000.00");
        assertThat(enListado.get("cantidadRiesgosActivos").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("Seguridad: un POST de escritura sin x-api-key responde 401 ProblemDetail y no modifica datos")
    void escrituraSinApiKeyNoModificaDatos() throws Exception {
        mvc.perform(post("/polizas/2/cancelar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/polizas/1/renovar").header(API_KEY_HEADER, "otra-clave"))
                .andExpect(status().isUnauthorized());

        ejecutar(get("/polizas/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.cantidadRiesgosActivos").value(2));
        ejecutar(get("/polizas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.valorCanon").value(1500000.00));
    }
}
