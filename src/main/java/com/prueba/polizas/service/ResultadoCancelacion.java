package com.prueba.polizas.service;

import com.prueba.polizas.model.Poliza;

/**
 * Resultado de cancelar una poliza.
 *
 * @param poliza                 poliza cancelada
 * @param cantidadRiesgosActivos riesgos activos tras la cancelacion (siempre 0)
 * @param riesgosCancelados      riesgos que estaban activos y se cancelaron (RN-03)
 */
public record ResultadoCancelacion(Poliza poliza, long cantidadRiesgosActivos, int riesgosCancelados) {
}
