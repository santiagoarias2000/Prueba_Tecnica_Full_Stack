package com.prueba.polizas.exception;

/** Violacion de una regla de negocio (HTTP 422). */
public class ReglaNegocioException extends NegocioException {

    public ReglaNegocioException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
