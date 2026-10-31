package com.prueba.polizas.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Body opcional de las cancelaciones de poliza y de riesgo.
 *
 * @param motivo motivo de la cancelacion (opcional)
 */
public record CancelacionRequest(
        @Size(max = 300, message = "debe tener maximo 300 caracteres") String motivo) {
}
