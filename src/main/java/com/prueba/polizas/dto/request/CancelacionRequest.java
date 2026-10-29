package com.prueba.polizas.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Body opcional de las cancelaciones de poliza y de riesgo (RF-04, RF-06).
 *
 * @param motivo motivo de la cancelacion (opcional)
 */
public record CancelacionRequest(
        @Size(max = 300, message = "debe tener maximo 300 caracteres") String motivo) {
}
