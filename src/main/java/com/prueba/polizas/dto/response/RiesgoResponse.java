package com.prueba.polizas.dto.response;

import com.prueba.polizas.model.enums.EstadoRiesgo;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Representacion de un riesgo. {@code fechaCancelacion} se serializa como {@code null} si no aplica. */
public record RiesgoResponse(
        Long id,
        Long polizaId,
        String descripcion,
        String arrendatario,
        String arrendador,
        BigDecimal valorCanon,
        EstadoRiesgo estado,
        LocalDate fechaInicio,
        LocalDate fechaCancelacion) {
}
