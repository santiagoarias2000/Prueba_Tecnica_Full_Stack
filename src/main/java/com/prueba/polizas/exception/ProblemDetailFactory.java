package com.prueba.polizas.exception;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Construye los {@link ProblemDetail} (RFC 7807) con el formato de 02c: {@code type}, {@code title},
 * {@code status}, {@code detail}, {@code instance} y las propiedades {@code codigo}, {@code timestamp}
 * y {@code correlationId}. Lo usan el {@link GlobalExceptionHandler} y el {@code ApiKeyFilter}.
 */
public final class ProblemDetailFactory {

    public static final String TYPE_BASE = "https://api.polizas.local/errores/";
    public static final String MDC_CORRELATION_ID = "correlationId";
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private ProblemDetailFactory() {
    }

    /** ProblemDetail para un codigo de error conocido; el status sale del propio codigo. */
    public static ProblemDetail crear(CodigoError codigo, String detail, String instance) {
        return crear(codigo.status(), codigo.name(), detail, instance);
    }

    /** ProblemDetail con status y codigo explicitos (para errores del framework sin {@link CodigoError}). */
    public static ProblemDetail crear(HttpStatusCode status, String codigo, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + codigo));
        problem.setTitle(titulo(status, codigo));
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("codigo", codigo);
        problem.setProperty("timestamp", ahora());
        problem.setProperty("correlationId", MDC.get(MDC_CORRELATION_ID));
        return problem;
    }

    /** Titulo segun la tabla de 02c. */
    static String titulo(HttpStatusCode status, String codigo) {
        return switch (status.value()) {
            case 400 -> "Solicitud inválida";
            case 401 -> "No autorizado";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 409 -> CodigoError.CONFLICTO_CONCURRENCIA.name().equals(codigo) ? "Conflicto" : "Estado inválido";
            case 415 -> "Tipo de contenido no soportado";
            case 422 -> "Regla de negocio";
            case 500 -> "Error interno";
            default -> {
                HttpStatus resuelto = HttpStatus.resolve(status.value());
                yield resuelto != null ? resuelto.getReasonPhrase() : "Error";
            }
        };
    }

    private static String ahora() {
        return OffsetDateTime.now(ZONA).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
