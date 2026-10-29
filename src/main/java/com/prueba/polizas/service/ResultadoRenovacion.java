package com.prueba.polizas.service;

import com.prueba.polizas.model.Poliza;
import java.math.BigDecimal;

/**
 * Resultado de renovar una poliza.
 *
 * @param poliza                 poliza renovada
 * @param cantidadRiesgosActivos riesgos activos tras la renovacion
 * @param ipcAplicado            IPC usado para ajustar los canones
 */
public record ResultadoRenovacion(Poliza poliza, long cantidadRiesgosActivos, BigDecimal ipcAplicado) {
}
