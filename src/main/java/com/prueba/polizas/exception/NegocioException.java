package com.prueba.polizas.exception;

import java.util.Objects;

/**
 * Base de las excepciones de negocio: todas llevan un {@link CodigoError}.
 */
public abstract class NegocioException extends RuntimeException {

    private final CodigoError codigo;

    protected NegocioException(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public CodigoError getCodigo() {
        return codigo;
    }
}
