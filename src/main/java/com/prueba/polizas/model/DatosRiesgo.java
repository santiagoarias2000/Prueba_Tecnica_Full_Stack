package com.prueba.polizas.model;

import java.math.BigDecimal;

/**
 * Datos necesarios para crear un riesgo dentro de una poliza.
 *
 * @param descripcion  direccion / identificacion del inmueble
 * @param arrendatario asegurado
 * @param arrendador   beneficiario
 * @param valorCanon   canon mensual del contrato de arrendamiento
 */
public record DatosRiesgo(String descripcion, String arrendatario, String arrendador, BigDecimal valorCanon) {
}
