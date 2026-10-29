package com.prueba.polizas.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Casos X1-X3 de 02g con el contexto completo (cadena de filtros real). Ningun test modifica datos:
 * las peticiones mutantes se rechazan con 401 antes de llegar al controller, y las autorizadas son GET.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("ApiKeyFilter (02g seccion D)")
class ApiKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /polizas",
            "GET, /polizas/2/riesgos",
            "POST, /polizas/1/renovar",
            "POST, /polizas/1/cancelar",
            "POST, /polizas/2/riesgos",
            "POST, /riesgos/10/cancelar",
            "POST, /core-mock/evento",
            "GET, /ruta-inexistente"
    })
    @DisplayName("X1: sin x-api-key cualquier endpoint responde 401 API_KEY_INVALIDA en problem+json")
    void debeRechazarSolicitudSinApiKey(String metodo, String ruta) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(metodo), ruta).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.polizas.local/errores/API_KEY_INVALIDA"))
                .andExpect(jsonPath("$.title").value("No autorizado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(notNullValue()))
                .andExpect(jsonPath("$.instance").value(ruta))
                .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @ParameterizedTest(name = "x-api-key=''{0}''")
    @ValueSource(strings = {"000", "", " 123456", "123456 ", "1234567", "12345"})
    @DisplayName("X2: x-api-key con valor incorrecto responde 401 API_KEY_INVALIDA")
    void debeRechazarApiKeyIncorrecta(String clave) throws Exception {
        mockMvc.perform(get("/polizas").header("x-api-key", clave))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"));
    }

    @Test
    @DisplayName("El valor de la API key recibida nunca se escribe en el log")
    void noDebeRegistrarElValorDeLaApiKey(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/polizas").header("x-api-key", "clave-secreta-qa-987"))
                .andExpect(status().isUnauthorized());

        assertThat(output.getAll()).contains("Solicitud rechazada").doesNotContain("clave-secreta-qa-987");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/actuator/health"})
    @DisplayName("X3: Swagger, OpenAPI y health son publicos (200 sin header)")
    void debePermitirRutasPublicasSinApiKey(String ruta) throws Exception {
        mockMvc.perform(get(ruta)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("X3: /swagger-ui.html sin header no responde 401 (redirige a la UI)")
    void debePermitirSwaggerUiHtmlSinApiKey() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/swagger-ui.html")).andReturn();
        assertThat(resultado.getResponse().getStatus()).isNotEqualTo(401).isLessThan(400);
    }

    @Test
    @DisplayName("El favicon que pide el navegador no exige x-api-key (no responde 401)")
    void debePermitirFaviconSinApiKey() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/favicon.ico")).andReturn();
        assertThat(resultado.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("X3: el documento OpenAPI declara el esquema de seguridad x-api-key")
    void debeDocumentarEsquemaApiKeyEnOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.name").value("x-api-key"));
    }

    @ParameterizedTest(name = "header {0}")
    @ValueSource(strings = {"x-api-key", "X-API-KEY", "X-Api-Key"})
    @DisplayName("Con la API key valida (nombre de header case-insensitive) la solicitud pasa")
    void debePermitirApiKeyValidaSinImportarMayusculasDelHeader(String nombreHeader) throws Exception {
        mockMvc.perform(get("/polizas").header(nombreHeader, "123456"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}
