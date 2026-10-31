package com.prueba.polizas.controller;

import static com.prueba.polizas.controller.Fixtures.API_KEY;
import static com.prueba.polizas.controller.Fixtures.API_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.polizas.config.PolizasProperties;
import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.EstadoInvalidoException;
import com.prueba.polizas.exception.RecursoNoEncontradoException;
import com.prueba.polizas.exception.ReglaNegocioException;
import com.prueba.polizas.model.DatosRiesgo;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import com.prueba.polizas.service.PolizaConConteo;
import com.prueba.polizas.service.PolizaService;
import com.prueba.polizas.service.ResultadoCancelacion;
import com.prueba.polizas.service.ResultadoRenovacion;
import com.prueba.polizas.service.RiesgoService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Pruebas de {@link PolizaController}: slice web real (filtros, converters, handler de errores)
 * con los services simulados.
 */
@WebMvcTest(controllers = {PolizaController.class, RiesgoController.class})
@EnableConfigurationProperties(PolizasProperties.class)
@DisplayName("PolizaController")
class PolizaControllerTest {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
    private static final String TYPE_BASE = "https://api.polizas.local/errores/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolizaService polizaService;

    @MockitoBean
    private RiesgoService riesgoService;

    private ResultActions ejecutar(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(API_KEY_HEADER, API_KEY));
    }

    private static ResultActions esperarProblema(ResultActions acciones, int status, String title, String codigo,
                                                 String instance) throws Exception {
        return acciones
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + codigo))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(notNullValue()))
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()));
    }

    @Nested
    @DisplayName("GET /polizas/{id}")
    class Obtener {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"/polizas/2"})
        @DisplayName("Poliza existente responde 200 con el mismo contrato del listado")
        void debeDevolverDetalle(String ruta) throws Exception {
            when(polizaService.obtener(2L)).thenReturn(new PolizaConConteo(Fixtures.polizaColectiva2(), 2));

            ejecutar(get(ruta))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(2))
                    .andExpect(jsonPath("$.numeroPoliza").value("COL-2026-0001"))
                    .andExpect(jsonPath("$.tipo").value("COLECTIVA"))
                    .andExpect(jsonPath("$.estado").value("ACTIVA"))
                    .andExpect(jsonPath("$.valorCanon").value(3200000.00))
                    .andExpect(jsonPath("$.valorPrima").value(19200000.00))
                    .andExpect(jsonPath("$.cantidadRiesgosActivos").value(2))
                    .andExpect(jsonPath("$.ipcAplicado").doesNotExist())
                    .andExpect(jsonPath("$.riesgosCancelados").doesNotExist());

            verify(polizaService).obtener(2L);
        }

        @Test
        @DisplayName("Poliza inexistente responde 404 POLIZA_NO_ENCONTRADA")
        void debeResponder404CuandoNoExiste() throws Exception {
            when(polizaService.obtener(999L)).thenThrow(new RecursoNoEncontradoException(
                    CodigoError.POLIZA_NO_ENCONTRADA, "No existe la poliza 999"));

            esperarProblema(ejecutar(get("/polizas/999")), 404, "Recurso no encontrado",
                    "POLIZA_NO_ENCONTRADA", "/polizas/999");
        }
    }

    @Nested
    @DisplayName("GET /polizas")
    class Listar {

        @Test
        @DisplayName("tipo=COLECTIVA&estado=ACTIVA responde 200 con array y pasa los enums al service")
        void debeListarPolizasFiltrandoPorTipoYEstado() throws Exception {
            when(polizaService.listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA))
                    .thenReturn(List.of(new PolizaConConteo(Fixtures.polizaColectiva2(), 2)));

            ejecutar(get("/polizas").param("tipo", "COLECTIVA").param("estado", "ACTIVA"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(2))
                    .andExpect(jsonPath("$[0].numeroPoliza").value("COL-2026-0001"))
                    .andExpect(jsonPath("$[0].tipo").value("COLECTIVA"))
                    .andExpect(jsonPath("$[0].estado").value("ACTIVA"))
                    .andExpect(jsonPath("$[0].tomador").value("Inmobiliaria Los Andes SAS"))
                    .andExpect(jsonPath("$[0].fechaInicioVigencia").value("2026-02-01"))
                    .andExpect(jsonPath("$[0].fechaFinVigencia").value("2026-07-31"))
                    .andExpect(jsonPath("$[0].mesesVigencia").value(6))
                    .andExpect(jsonPath("$[0].valorCanon").value(3200000.00))
                    .andExpect(jsonPath("$[0].valorPrima").value(19200000.00))
                    .andExpect(jsonPath("$[0].cantidadRiesgosActivos").value(2))
                    .andExpect(jsonPath("$[0].ipcAplicado").doesNotExist())
                    .andExpect(jsonPath("$[0].riesgosCancelados").doesNotExist());

            verify(polizaService).listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA);
        }

        @Test
        @DisplayName("Sin filtros invoca al service con null/null (todas las polizas)")
        void debeListarTodasCuandoNoHayFiltros() throws Exception {
            when(polizaService.listar(null, null)).thenReturn(List.of(
                    new PolizaConConteo(Fixtures.polizaIndividual1(), 1),
                    new PolizaConConteo(Fixtures.polizaColectiva2(), 2)));

            ejecutar(get("/polizas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].valorCanon").value(1500000.00))
                    .andExpect(jsonPath("$[0].valorPrima").value(18000000.00));

            verify(polizaService).listar(null, null);
        }

        @ParameterizedTest(name = "tipo={0}, estado={1}")
        @CsvSource({"colectiva, activa", "Colectiva, Activa", "' COLECTIVA ', aCtIvA"})
        @DisplayName("Los filtros son case-insensitive")
        void debeAceptarFiltrosSinImportarMayusculas(String tipo, String estado) throws Exception {
            when(polizaService.listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA)).thenReturn(List.of());

            ejecutar(get("/polizas").param("tipo", tipo).param("estado", estado))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(polizaService).listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA);
        }

        @Test
        @DisplayName("tipo=XYZ responde 400 PARAMETRO_INVALIDO sin invocar al service")
        void debeRechazarTipoInvalido() throws Exception {
            esperarProblema(ejecutar(get("/polizas").param("tipo", "XYZ")), 400, "Solicitud inválida",
                    "PARAMETRO_INVALIDO", "/polizas")
                    .andExpect(jsonPath("$.detail").value(containsString("tipo")))
                    .andExpect(jsonPath("$.detail").value(containsString("INDIVIDUAL, COLECTIVA")));

            verifyNoInteractions(polizaService);
        }

        @Test
        @DisplayName("estado=XYZ responde 400 PARAMETRO_INVALIDO")
        void debeRechazarEstadoInvalido() throws Exception {
            esperarProblema(ejecutar(get("/polizas").param("estado", "VIGENTE")), 400, "Solicitud inválida",
                    "PARAMETRO_INVALIDO", "/polizas")
                    .andExpect(jsonPath("$.detail").value(containsString("estado")));

            verifyNoInteractions(polizaService);
        }
    }

    @Nested
    @DisplayName("GET /polizas/{id}/riesgos")
    class ListarRiesgos {

        @Test
        @DisplayName("Responde 200 con los riesgos y fechaCancelacion explicita en null")
        void debeListarRiesgosDeLaPoliza() throws Exception {
            when(polizaService.listarRiesgos(2L)).thenReturn(Fixtures.polizaColectiva2().getRiesgos());

            ejecutar(get("/polizas/2/riesgos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].polizaId").value(2))
                    .andExpect(jsonPath("$[0].descripcion").value("Apto 301 - Cra 15 # 93-20, Bogotá"))
                    .andExpect(jsonPath("$[0].arrendatario").value("Ana Gómez"))
                    .andExpect(jsonPath("$[0].arrendador").value("Inversiones Andes SAS"))
                    .andExpect(jsonPath("$[0].valorCanon").value(2000000.00))
                    .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                    .andExpect(jsonPath("$[0].fechaInicio").value("2026-02-01"))
                    .andExpect(jsonPath("$[0].fechaCancelacion").hasJsonPath())
                    .andExpect(jsonPath("$[0].fechaCancelacion").isEmpty())
                    .andExpect(jsonPath("$[1].id").value(11))
                    .andExpect(jsonPath("$[1].valorCanon").value(1200000.00));
        }

        @Test
        @DisplayName("poliza inexistente responde 404 POLIZA_NO_ENCONTRADA")
        void debeResponder404CuandoLaPolizaNoExiste() throws Exception {
            when(polizaService.listarRiesgos(999L)).thenThrow(new RecursoNoEncontradoException(
                    CodigoError.POLIZA_NO_ENCONTRADA, "No existe la poliza 999"));

            esperarProblema(ejecutar(get("/polizas/999/riesgos")), 404, "Recurso no encontrado",
                    "POLIZA_NO_ENCONTRADA", "/polizas/999/riesgos")
                    .andExpect(jsonPath("$.detail").value("No existe la poliza 999"));
        }

        @Test
        @DisplayName("Id no numerico responde 400 PARAMETRO_INVALIDO")
        void debeRechazarIdNoNumerico() throws Exception {
            esperarProblema(ejecutar(get("/polizas/abc/riesgos")), 400, "Solicitud inválida",
                    "PARAMETRO_INVALIDO", "/polizas/abc/riesgos");
            verifyNoInteractions(polizaService);
        }
    }

    @Nested
    @DisplayName("POST /polizas/{id}/renovar")
    class Renovar {

        @Test
        @DisplayName("Sin body responde 200 con la poliza renovada (D1: 1.576.500 / 18.918.000) e ipcAplicado 0.0510")
        void debeRenovarConIpcConfigurado() throws Exception {
            Poliza poliza = Fixtures.polizaIndividual1();
            poliza.renovar(new BigDecimal("0.051"));
            when(polizaService.renovar(eq(1L), isNull()))
                    .thenReturn(new ResultadoRenovacion(poliza, 1, new BigDecimal("0.051")));

            ejecutar(post("/polizas/1/renovar"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.numeroPoliza").value("IND-2026-0001"))
                    .andExpect(jsonPath("$.tipo").value("INDIVIDUAL"))
                    .andExpect(jsonPath("$.estado").value("RENOVADA"))
                    .andExpect(jsonPath("$.fechaInicioVigencia").value("2027-01-01"))
                    .andExpect(jsonPath("$.fechaFinVigencia").value("2027-12-31"))
                    .andExpect(jsonPath("$.mesesVigencia").value(12))
                    .andExpect(jsonPath("$.valorCanon").value(1576500.00))
                    .andExpect(jsonPath("$.valorPrima").value(18918000.00))
                    .andExpect(jsonPath("$.cantidadRiesgosActivos").value(1))
                    .andExpect(content().string(containsString("\"ipcAplicado\":0.0510")))
                    .andExpect(jsonPath("$.riesgosCancelados").doesNotExist());

            verify(polizaService).renovar(eq(1L), isNull());
        }

        @Test
        @DisplayName("Body {\"ipc\": 0.0600} sobreescribe el IPC configurado")
        void debePasarElIpcDelBodyAlService() throws Exception {
            Poliza poliza = Fixtures.polizaIndividual1();
            poliza.renovar(new BigDecimal("0.0600"));
            when(polizaService.renovar(eq(1L), any()))
                    .thenReturn(new ResultadoRenovacion(poliza, 1, new BigDecimal("0.0600")));

            ejecutar(post("/polizas/1/renovar").contentType(MediaType.APPLICATION_JSON).content("{\"ipc\": 0.0600}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valorCanon").value(1590000.00))
                    .andExpect(jsonPath("$.ipcAplicado").value(0.06));

            ArgumentCaptor<BigDecimal> ipc = ArgumentCaptor.forClass(BigDecimal.class);
            verify(polizaService).renovar(eq(1L), ipc.capture());
            assertThat(ipc.getValue()).isEqualByComparingTo("0.06");
        }

        @ParameterizedTest(name = "ipc={0}")
        @CsvSource({"-0.01", "1.5"})
        @DisplayName("IPC fuera de [0, 1] responde 400 VALIDACION con errores[] sobre 'ipc'")
        void debeRechazarIpcFueraDeRango(String ipc) throws Exception {
            esperarProblema(ejecutar(post("/polizas/1/renovar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ipc\": " + ipc + "}")), 400, "Solicitud inválida", "VALIDACION", "/polizas/1/renovar")
                    .andExpect(jsonPath("$.errores[0].campo").value("ipc"));

            verifyNoInteractions(polizaService);
        }

        @Test
        @DisplayName("poliza cancelada responde 409 POLIZA_CANCELADA_NO_RENOVABLE (title 'Estado inválido')")
        void debeRechazarRenovacionCuandoPolizaEstaCancelada() throws Exception {
            when(polizaService.renovar(eq(3L), isNull())).thenThrow(new EstadoInvalidoException(
                    CodigoError.POLIZA_CANCELADA_NO_RENOVABLE,
                    "La poliza IND-2026-0002 esta cancelada y no se puede renovar"));

            esperarProblema(ejecutar(post("/polizas/3/renovar")), 409, "Estado inválido",
                    "POLIZA_CANCELADA_NO_RENOVABLE", "/polizas/3/renovar")
                    .andExpect(jsonPath("$.detail").value("La poliza IND-2026-0002 esta cancelada y no se puede renovar"));
        }

        @Test
        @DisplayName("poliza inexistente responde 404 POLIZA_NO_ENCONTRADA")
        void debeResponder404AlRenovarPolizaInexistente() throws Exception {
            when(polizaService.renovar(eq(999L), isNull())).thenThrow(new RecursoNoEncontradoException(
                    CodigoError.POLIZA_NO_ENCONTRADA, "No existe la poliza 999"));

            esperarProblema(ejecutar(post("/polizas/999/renovar")), 404, "Recurso no encontrado",
                    "POLIZA_NO_ENCONTRADA", "/polizas/999/renovar");
        }

        @Test
        @DisplayName("Bloqueo optimista responde 409 CONFLICTO_CONCURRENCIA (title 'Conflicto')")
        void debeResponder409CuandoHayConflictoDeConcurrencia() throws Exception {
            when(polizaService.renovar(eq(1L), isNull()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Poliza.class, 1L));

            esperarProblema(ejecutar(post("/polizas/1/renovar")), 409, "Conflicto", "CONFLICTO_CONCURRENCIA",
                    "/polizas/1/renovar");
        }

        @Test
        @DisplayName("Error no controlado responde 500 ERROR_INTERNO sin stacktrace ni mensaje interno")
        void debeOcultarDetallesInternosEnError500() throws Exception {
            when(polizaService.renovar(eq(1L), isNull()))
                    .thenThrow(new IllegalStateException("fallo interno con dato sensible jdbc:h2"));

            esperarProblema(ejecutar(post("/polizas/1/renovar")), 500, "Error interno", "ERROR_INTERNO",
                    "/polizas/1/renovar")
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.exception").doesNotExist())
                    .andExpect(content().string(not(containsString("dato sensible"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))))
                    .andExpect(content().string(not(containsString("at com.prueba"))));
        }
    }

    @Nested
    @DisplayName("POST /polizas/{id}/cancelar")
    class Cancelar {

        @Test
        @DisplayName("Responde 200 con estado CANCELADA, riesgosCancelados=2 y pasa el motivo al service")
        void debeCancelarPolizaYReportarRiesgosCancelados() throws Exception {
            Poliza poliza = Fixtures.polizaColectiva2();
            int cancelados = poliza.cancelar("Solicitud del tomador");
            when(polizaService.cancelar(2L, "Solicitud del tomador"))
                    .thenReturn(new ResultadoCancelacion(poliza, 0, cancelados));

            ejecutar(post("/polizas/2/cancelar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"motivo\": \"Solicitud del tomador\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(2))
                    .andExpect(jsonPath("$.estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.riesgosCancelados").value(2))
                    .andExpect(jsonPath("$.cantidadRiesgosActivos").value(0))
                    .andExpect(jsonPath("$.valorCanon").value(3200000.00))
                    .andExpect(jsonPath("$.ipcAplicado").doesNotExist());

            verify(polizaService).cancelar(2L, "Solicitud del tomador");
        }

        @Test
        @DisplayName("Sin body cancela con motivo null")
        void debeCancelarSinBody() throws Exception {
            Poliza poliza = Fixtures.polizaIndividual1();
            int cancelados = poliza.cancelar(null);
            when(polizaService.cancelar(eq(1L), isNull()))
                    .thenReturn(new ResultadoCancelacion(poliza, 0, cancelados));

            ejecutar(post("/polizas/1/cancelar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.riesgosCancelados").value(1));
        }

        @Test
        @DisplayName("poliza ya cancelada responde 409 POLIZA_YA_CANCELADA")
        void debeRechazarCancelarPolizaYaCancelada() throws Exception {
            when(polizaService.cancelar(eq(3L), isNull())).thenThrow(new EstadoInvalidoException(
                    CodigoError.POLIZA_YA_CANCELADA, "La poliza IND-2026-0002 ya se encuentra cancelada"));

            esperarProblema(ejecutar(post("/polizas/3/cancelar")), 409, "Estado inválido", "POLIZA_YA_CANCELADA",
                    "/polizas/3/cancelar");
        }

        @Test
        @DisplayName("Motivo de mas de 300 caracteres responde 400 VALIDACION")
        void debeRechazarMotivoDemasiadoLargo() throws Exception {
            esperarProblema(ejecutar(post("/polizas/2/cancelar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"motivo\": \"" + "x".repeat(301) + "\"}")), 400, "Solicitud inválida", "VALIDACION",
                    "/polizas/2/cancelar")
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"));

            verifyNoInteractions(polizaService);
        }
    }

    @Nested
    @DisplayName("POST /polizas/{id}/riesgos")
    class AgregarRiesgo {

        private static final String BODY_VALIDO = """
                {
                  "descripcion": "Apto 502 - Calle 100 # 10-20, Bogotá",
                  "arrendatario": "Luis Martínez",
                  "arrendador": "María Rodríguez",
                  "valorCanon": 1800000.00
                }""";

        @Test
        @DisplayName("canon negativo y campos vacios responden 400 VALIDACION con errores[] por campo")
        void debeRechazarBodyInvalido() throws Exception {
            String body = """
                    {"descripcion": "", "arrendatario": "   ", "valorCanon": -100}""";

            esperarProblema(ejecutar(post("/polizas/1/riesgos").contentType(MediaType.APPLICATION_JSON).content(body)),
                    400, "Solicitud inválida", "VALIDACION", "/polizas/1/riesgos")
                    .andExpect(jsonPath("$.errores").isArray())
                    .andExpect(jsonPath("$.errores", hasSize(4)))
                    .andExpect(jsonPath("$.errores[*].campo")
                            .value(containsInAnyOrder("arrendador", "arrendatario", "descripcion", "valorCanon")))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'valorCanon')].mensaje")
                            .value(containsInAnyOrder("debe ser mayor que cero")))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'descripcion')].mensaje")
                            .value(containsInAnyOrder("es obligatorio")));

            verifyNoInteractions(polizaService);
        }

        @Test
        @DisplayName("Canon con mas de 2 decimales y textos de mas de 200 caracteres responden 400 VALIDACION")
        void debeRechazarDigitosYLongitudes() throws Exception {
            String largo = "a".repeat(201);
            String body = "{\"descripcion\": \"" + largo + "\", \"arrendatario\": \"Luis\", \"arrendador\": \"Maria\","
                    + " \"valorCanon\": 1800000.123}";

            esperarProblema(ejecutar(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON).content(body)),
                    400, "Solicitud inválida", "VALIDACION", "/polizas/2/riesgos")
                    .andExpect(jsonPath("$.errores[*].campo").value(containsInAnyOrder("descripcion", "valorCanon")));
        }

        @Test
        @DisplayName("JSON mal formado o tipo incorrecto responde 400 VALIDACION")
        void debeRechazarJsonMalFormado() throws Exception {
            esperarProblema(ejecutar(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"valorCanon\": \"mucho\"")), 400, "Solicitud inválida", "VALIDACION",
                    "/polizas/2/riesgos");
        }

        @Test
        @DisplayName("Body ausente responde 400")
        void debeRechazarBodyAusente() throws Exception {
            esperarProblema(ejecutar(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON)), 400,
                    "Solicitud inválida", "VALIDACION", "/polizas/2/riesgos");
        }

        @Test
        @DisplayName("Content-Type distinto de JSON responde 415")
        void debeRechazarContentTypeNoSoportado() throws Exception {
            esperarProblema(ejecutar(post("/polizas/2/riesgos").contentType(MediaType.TEXT_PLAIN).content("hola")),
                    415, "Tipo de contenido no soportado", "TIPO_CONTENIDO_NO_SOPORTADO", "/polizas/2/riesgos");
        }

        @Test
        @DisplayName("body valido responde 201 + Location /riesgos/{id} + riesgo creado")
        void debeCrearRiesgoEnPolizaColectiva() throws Exception {
            Poliza poliza = Fixtures.polizaColectiva2();
            Riesgo creado = Fixtures.conId(poliza.agregarRiesgo("Apto 502 - Calle 100 # 10-20, Bogotá",
                    "Luis Martínez", "María Rodríguez", new BigDecimal("1800000.00")), 100L);
            when(polizaService.agregarRiesgo(eq(2L), any(DatosRiesgo.class))).thenReturn(creado);

            ejecutar(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/riesgos/100"))
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.polizaId").value(2))
                    .andExpect(jsonPath("$.descripcion").value("Apto 502 - Calle 100 # 10-20, Bogotá"))
                    .andExpect(jsonPath("$.arrendatario").value("Luis Martínez"))
                    .andExpect(jsonPath("$.arrendador").value("María Rodríguez"))
                    .andExpect(jsonPath("$.valorCanon").value(1800000.00))
                    .andExpect(jsonPath("$.estado").value("ACTIVO"))
                    .andExpect(jsonPath("$.fechaInicio").value(notNullValue()))
                    .andExpect(jsonPath("$.fechaCancelacion").isEmpty());

            ArgumentCaptor<DatosRiesgo> datos = ArgumentCaptor.forClass(DatosRiesgo.class);
            verify(polizaService).agregarRiesgo(eq(2L), datos.capture());
            assertThat(datos.getValue().descripcion()).isEqualTo("Apto 502 - Calle 100 # 10-20, Bogotá");
            assertThat(datos.getValue().arrendatario()).isEqualTo("Luis Martínez");
            assertThat(datos.getValue().arrendador()).isEqualTo("María Rodríguez");
            assertThat(datos.getValue().valorCanon()).isEqualByComparingTo("1800000.00");
        }

        @Test
        @DisplayName("poliza individual responde 422 TIPO_POLIZA_NO_PERMITE_RIESGOS (title 'Regla de negocio')")
        void debeRechazarRiesgoEnPolizaIndividual() throws Exception {
            when(polizaService.agregarRiesgo(eq(1L), any())).thenThrow(new ReglaNegocioException(
                    CodigoError.TIPO_POLIZA_NO_PERMITE_RIESGOS, "Solo las polizas colectivas permiten agregar riesgos"));

            esperarProblema(ejecutar(post("/polizas/1/riesgos").contentType(MediaType.APPLICATION_JSON)
                            .content(BODY_VALIDO)), 422, "Regla de negocio", "TIPO_POLIZA_NO_PERMITE_RIESGOS",
                    "/polizas/1/riesgos");
        }

        @Test
        @DisplayName("poliza cancelada responde 409 POLIZA_CANCELADA")
        void debeRechazarRiesgoEnPolizaCancelada() throws Exception {
            when(polizaService.agregarRiesgo(eq(3L), any())).thenThrow(new EstadoInvalidoException(
                    CodigoError.POLIZA_CANCELADA, "No se puede agregar un riesgo a una poliza cancelada"));

            esperarProblema(ejecutar(post("/polizas/3/riesgos").contentType(MediaType.APPLICATION_JSON)
                    .content(BODY_VALIDO)), 409, "Estado inválido", "POLIZA_CANCELADA", "/polizas/3/riesgos");
        }

        @Test
        @DisplayName("poliza inexistente responde 404 POLIZA_NO_ENCONTRADA")
        void debeResponder404AlAgregarRiesgoAPolizaInexistente() throws Exception {
            when(polizaService.agregarRiesgo(anyLong(), any())).thenThrow(new RecursoNoEncontradoException(
                    CodigoError.POLIZA_NO_ENCONTRADA, "No existe la poliza 999"));

            esperarProblema(ejecutar(post("/polizas/999/riesgos").contentType(MediaType.APPLICATION_JSON)
                    .content(BODY_VALIDO)), 404, "Recurso no encontrado", "POLIZA_NO_ENCONTRADA",
                    "/polizas/999/riesgos");
        }
    }

    @Nested
    @DisplayName("Errores transversales")
    class Transversales {

        @Test
        @DisplayName("El correlationId recibido en X-Correlation-Id se devuelve en el header y en el ProblemDetail")
        void debePropagarCorrelationIdEnErrores() throws Exception {
            when(polizaService.listarRiesgos(999L)).thenThrow(new RecursoNoEncontradoException(
                    CodigoError.POLIZA_NO_ENCONTRADA, "No existe la poliza 999"));

            ejecutar(get("/polizas/999/riesgos").header("X-Correlation-Id", "prueba-qa-123"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("X-Correlation-Id", "prueba-qa-123"))
                    .andExpect(jsonPath("$.correlationId").value("prueba-qa-123"));
        }

        @Test
        @DisplayName("Metodo no soportado responde 405 METODO_NO_PERMITIDO")
        void debeResponder405ConMetodoNoSoportado() throws Exception {
            esperarProblema(ejecutar(delete("/polizas")), 405, "Método no permitido", "METODO_NO_PERMITIDO",
                    "/polizas");
        }

        @Test
        @DisplayName("Ruta inexistente responde 404 RECURSO_NO_ENCONTRADO")
        void debeResponder404ConRutaInexistente() throws Exception {
            esperarProblema(ejecutar(get("/no-existe")), 404, "Recurso no encontrado", "RECURSO_NO_ENCONTRADO",
                    "/no-existe");
        }

        @Test
        @DisplayName("El filtro de API key esta activo en el slice web: sin header responde 401")
        void debeExigirApiKeyEnElSliceWeb() throws Exception {
            mockMvc.perform(get("/polizas"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"));
            verifyNoInteractions(polizaService);
        }
    }
}
