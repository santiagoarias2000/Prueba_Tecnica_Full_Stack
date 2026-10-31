package com.prueba.polizas.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * Body opcional de {@code POST /polizas/{id}/renovar}.
 *
 * @param ipc IPC a aplicar como fraccion (0.0510 = 5,10 %); si es {@code null} se usa {@code polizas.ipc.valor}
 */
public record RenovacionRequest(
        @DecimalMin(value = "0", message = "debe ser mayor o igual a 0")
        @DecimalMax(value = "1", message = "debe ser menor o igual a 1")
        BigDecimal ipc) {
}
