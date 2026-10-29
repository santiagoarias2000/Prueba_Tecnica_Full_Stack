package com.prueba.polizas.exception;

/**
 * Error de validacion de un campo, expuesto en la propiedad {@code errores} del {@code ProblemDetail}.
 *
 * @param campo   nombre del campo (o del objeto si es un error global)
 * @param mensaje descripcion del error
 */
public record ErrorCampo(String campo, String mensaje) {
}
