package com.prueba.polizas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /core-mock/evento} (RF-07). {@code operacion} y {@code riesgoId} son opcionales.
 *
 * @param evento    tipo de evento (p. ej. {@code ACTUALIZACION})
 * @param polizaId  poliza afectada
 * @param operacion detalle de la operacion (opcional)
 * @param riesgoId  riesgo afectado (opcional)
 */
public record CoreEventoRequest(
        @NotBlank String evento,
        @NotNull Long polizaId,
        String operacion,
        Long riesgoId) {
}
