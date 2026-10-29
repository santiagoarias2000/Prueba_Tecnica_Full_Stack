package com.prueba.polizas.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.prueba.polizas.exception.ProblemDetailFactory;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    /** Ejecuta el filtro y devuelve el valor del MDC visto dentro de la cadena. */
    private String filtrar(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> enMdc = new AtomicReference<>();
        FilterChain cadena = (req, res) -> enMdc.set(MDC.get(ProblemDetailFactory.MDC_CORRELATION_ID));
        filtro.doFilter(request, response, cadena);
        return enMdc.get();
    }

    @Test
    @DisplayName("Propaga el X-Correlation-Id recibido al MDC y al header de respuesta")
    void debePropagarCorrelationIdRecibido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/polizas");
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123_XYZ.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String enMdc = filtrar(request, response);

        assertThat(enMdc).isEqualTo("abc-123_XYZ.9");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123_XYZ.9");
    }

    @Test
    @DisplayName("Genera un UUID cuando no llega el header")
    void debeGenerarCorrelationIdCuandoNoLlega() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String enMdc = filtrar(new MockHttpServletRequest("GET", "/polizas"), response);

        assertThat(enMdc).isNotBlank();
        assertThat(UUID.fromString(enMdc)).isNotNull();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(enMdc);
    }

    @ParameterizedTest(name = "''{0}''")
    @ValueSource(strings = {"", "con espacio", "inyeccion\nFALSO LOG", "<script>"})
    @DisplayName("Reemplaza por un UUID un header con formato inseguro (evita log injection)")
    void debeReemplazarCorrelationIdConFormatoInvalido(String recibido) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/polizas");
        request.addHeader(CorrelationIdFilter.HEADER, recibido);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String enMdc = filtrar(request, response);

        assertThat(enMdc).isNotEqualTo(recibido);
        assertThat(UUID.fromString(enMdc)).isNotNull();
    }

    @Test
    @DisplayName("Rechaza ids de mas de 64 caracteres")
    void debeReemplazarCorrelationIdDemasiadoLargo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/polizas");
        request.addHeader(CorrelationIdFilter.HEADER, "a".repeat(65));

        assertThat(filtrar(request, new MockHttpServletResponse())).hasSize(36);
    }

    @Test
    @DisplayName("Limpia el MDC al terminar la peticion")
    void debeLimpiarMdcAlTerminar() throws Exception {
        filtrar(new MockHttpServletRequest("GET", "/polizas"), new MockHttpServletResponse());

        assertThat(MDC.get(ProblemDetailFactory.MDC_CORRELATION_ID)).isNull();
    }
}
