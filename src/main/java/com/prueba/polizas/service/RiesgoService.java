package com.prueba.polizas.service;

import com.prueba.polizas.model.Riesgo;

/** Casos de uso sobre riesgos individuales. */
public interface RiesgoService {

    /**
     * Cancela un riesgo de una poliza COLECTIVA y recalcula la poliza.
     * 404 {@code RIESGO_NO_ENCONTRADO} si no existe. Publica {@code CambioEstadoEvent} (CANCELAR_RIESGO).
     */
    Riesgo cancelar(Long riesgoId, String motivo);
}
