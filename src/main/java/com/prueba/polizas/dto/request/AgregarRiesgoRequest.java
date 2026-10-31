package com.prueba.polizas.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Body de {@code POST /polizas/{id}/riesgos}.
 *
 * @param descripcion  direccion / identificacion del inmueble
 * @param arrendatario asegurado
 * @param arrendador   beneficiario
 * @param valorCanon   canon mensual del contrato
 */
public record AgregarRiesgoRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 200, message = "debe tener maximo 200 caracteres")
        String descripcion,
        @NotBlank(message = "es obligatorio") @Size(max = 200, message = "debe tener maximo 200 caracteres")
        String arrendatario,
        @NotBlank(message = "es obligatorio") @Size(max = 200, message = "debe tener maximo 200 caracteres")
        String arrendador,
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 13, fraction = 2, message = "debe tener maximo 13 enteros y 2 decimales")
        BigDecimal valorCanon) {
}
