package com.prueba.polizas.integration.core;

import com.prueba.polizas.model.enums.OperacionCore;

/**
 * Body enviado al CORE: {@code {"evento":"ACTUALIZACION","polizaId":1,"operacion":"RENOVAR_POLIZA","riesgoId":null}}.
 *
 * @param evento    siempre {@value #EVENTO_ACTUALIZACION} (literal del enunciado)
 * @param polizaId  poliza afectada
 * @param operacion detalle de la operacion
 * @param riesgoId  riesgo afectado o {@code null}
 */
public record CoreEventoPayload(String evento, Long polizaId, OperacionCore operacion, Long riesgoId) {

    public static final String EVENTO_ACTUALIZACION = "ACTUALIZACION";

    public static CoreEventoPayload desde(CambioEstadoEvent evento) {
        return new CoreEventoPayload(EVENTO_ACTUALIZACION, evento.polizaId(), evento.operacion(), evento.riesgoId());
    }
}
