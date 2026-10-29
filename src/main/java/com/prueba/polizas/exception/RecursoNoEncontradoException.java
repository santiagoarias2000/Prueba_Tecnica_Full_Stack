package com.prueba.polizas.exception;

/** Poliza o riesgo inexistente (HTTP 404). */
public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
