package com.prueba.polizas.config;

import com.prueba.polizas.exception.ProblemDetailFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propaga un identificador de correlacion: usa {@code X-Correlation-Id} si llega con un formato seguro
 * (evita inyeccion en logs) o genera un UUID. Lo deja en el MDC y en el header de respuesta.
 * Se ejecuta antes que el {@code ApiKeyFilter} para que los 401 tambien lleven correlationId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        String correlationId = recibido != null && FORMATO_VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        MDC.put(ProblemDetailFactory.MDC_CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(ProblemDetailFactory.MDC_CORRELATION_ID);
        }
    }
}
