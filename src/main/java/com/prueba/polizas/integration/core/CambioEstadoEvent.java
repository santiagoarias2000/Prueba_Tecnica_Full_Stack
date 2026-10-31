package com.prueba.polizas.integration.core;

import com.prueba.polizas.model.enums.OperacionCore;
import java.util.Objects;

/**
 * Evento de aplicacion publicado por los services tras un cambio de estado exitoso.
 * Se notifica al CORE despues del commit.
 *
 * @param polizaId  poliza afectada
 * @param riesgoId  riesgo afectado ({@code null} en operaciones sobre la poliza)
 * @param operacion operacion realizada
 */
public record CambioEstadoEvent(Long polizaId, Long riesgoId, OperacionCore operacion) {

    public CambioEstadoEvent {
        Objects.requireNonNull(operacion, "operacion");
    }
}
