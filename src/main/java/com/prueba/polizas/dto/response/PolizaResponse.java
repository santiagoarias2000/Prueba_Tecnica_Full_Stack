package com.prueba.polizas.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Representacion de una poliza. {@code ipcAplicado} solo aparece al renovar y
 * {@code riesgosCancelados} solo al cancelar; en el resto de respuestas se omiten.
 */
public record PolizaResponse(
        Long id,
        String numeroPoliza,
        TipoPoliza tipo,
        EstadoPoliza estado,
        String tomador,
        LocalDate fechaInicioVigencia,
        LocalDate fechaFinVigencia,
        Integer mesesVigencia,
        BigDecimal valorCanon,
        BigDecimal valorPrima,
        long cantidadRiesgosActivos,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal ipcAplicado,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer riesgosCancelados) {
}
