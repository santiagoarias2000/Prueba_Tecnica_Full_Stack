package com.prueba.polizas.exception;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error expuestos en el campo {@code codigo} del {@code ProblemDetail} (ver 02b / 02c).
 */
public enum CodigoError {
    POLIZA_INDIVIDUAL_UN_RIESGO(HttpStatus.UNPROCESSABLE_ENTITY),
    TIPO_POLIZA_NO_PERMITE_RIESGOS(HttpStatus.UNPROCESSABLE_ENTITY),
    RIESGO_INDIVIDUAL_NO_CANCELABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    POLIZA_CANCELADA_NO_RENOVABLE(HttpStatus.CONFLICT),
    POLIZA_YA_CANCELADA(HttpStatus.CONFLICT),
    POLIZA_CANCELADA(HttpStatus.CONFLICT),
    RIESGO_YA_CANCELADO(HttpStatus.CONFLICT),
    CONFLICTO_CONCURRENCIA(HttpStatus.CONFLICT),
    POLIZA_NO_ENCONTRADA(HttpStatus.NOT_FOUND),
    RIESGO_NO_ENCONTRADO(HttpStatus.NOT_FOUND),
    /** Ruta HTTP inexistente (no es un recurso de dominio). */
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND),
    /** Metodo HTTP no soportado por la ruta. */
    METODO_NO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED),
    /** Content-Type del body no soportado. */
    TIPO_CONTENIDO_NO_SOPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    PARAMETRO_INVALIDO(HttpStatus.BAD_REQUEST),
    VALIDACION(HttpStatus.BAD_REQUEST),
    API_KEY_INVALIDA(HttpStatus.UNAUTHORIZED),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    CodigoError(HttpStatus status) {
        this.status = status;
    }

    /** Estado HTTP asociado al codigo. */
    public HttpStatus status() {
        return status;
    }
}
