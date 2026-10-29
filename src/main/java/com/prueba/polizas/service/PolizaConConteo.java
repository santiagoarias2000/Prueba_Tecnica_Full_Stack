package com.prueba.polizas.service;

import com.prueba.polizas.model.Poliza;

/**
 * Poliza con su numero de riesgos activos, calculado dentro de la transaccion (sin N+1 ni acceso lazy).
 *
 * @param poliza                 poliza
 * @param cantidadRiesgosActivos riesgos en estado ACTIVO
 */
public record PolizaConConteo(Poliza poliza, long cantidadRiesgosActivos) {
}
