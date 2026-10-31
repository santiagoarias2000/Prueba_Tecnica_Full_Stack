package com.prueba.polizas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.polizas.config.PolizasProperties;
import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.ProblemDetailFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Seguridad minima: exige el header configurado ({@code x-api-key}) con el valor esperado,
 * comparando en tiempo constante. Si falla responde 401 {@code API_KEY_INVALIDA} y corta la cadena.
 * Nunca registra el valor recibido.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    /** Rutas publicas (documentacion, consola H2, health y pagina de error). */
    static final List<String> RUTAS_EXCLUIDAS = List.of(
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/h2-console/**",
            "/actuator/health/**", "/error", "/favicon.ico");

    private final List<PathPattern> exclusiones;
    private final String header;
    private final byte[] apiKeyEsperada;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(PolizasProperties properties, ObjectMapper objectMapper) {
        this.header = properties.seguridad().header();
        this.apiKeyEsperada = properties.seguridad().apiKey().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.exclusiones = RUTAS_EXCLUIDAS.stream().map(parser::parse).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer ruta = PathContainer.parsePath(rutaSinContexto(request));
        return exclusiones.stream().anyMatch(p -> p.matches(ruta));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibida = request.getHeader(header);
        if (recibida != null && MessageDigest.isEqual(apiKeyEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Solicitud rechazada {} {}: header {} {}", request.getMethod(), request.getRequestURI(), header,
                recibida == null ? "ausente" : "invalido");
        rechazar(request, response);
    }

    private void rechazar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetailFactory.crear(CodigoError.API_KEY_INVALIDA,
                "El header " + header + " es obligatorio y debe tener un valor valido", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static String rutaSinContexto(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return contexto != null && !contexto.isEmpty() && uri.startsWith(contexto)
                ? uri.substring(contexto.length())
                : uri;
    }
}
