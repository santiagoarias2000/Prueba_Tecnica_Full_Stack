package com.prueba.polizas.exception;

/** Transicion de estado no permitida (HTTP 409). */
public class EstadoInvalidoException extends NegocioException {

    public EstadoInvalidoException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
