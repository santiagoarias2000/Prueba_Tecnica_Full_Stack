package com.prueba.polizas.service;

import com.prueba.polizas.model.DatosRiesgo;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import java.math.BigDecimal;
import java.util.List;

/** Casos de uso sobre polizas. Toda operacion de cambio de estado publica un {@code CambioEstadoEvent}. */
public interface PolizaService {

    /** Lista polizas filtrando por tipo y/o estado ({@code null} = sin filtro), con su conteo de riesgos activos. */
    List<PolizaConConteo> listar(TipoPoliza tipo, EstadoPoliza estado);

    /** Extra EX-01: detalle de una poliza con su conteo de riesgos activos. 404 {@code POLIZA_NO_ENCONTRADA}. */
    PolizaConConteo obtener(Long polizaId);

    /** Riesgos de una poliza ordenados por id. 404 {@code POLIZA_NO_ENCONTRADA} si no existe. */
    List<Riesgo> listarRiesgos(Long polizaId);

    /** Renueva la poliza con el IPC vigente (RN-05, RN-02, RN-06). */
    ResultadoRenovacion renovar(Long polizaId);

    /**
     * Renueva la poliza con el IPC indicado; si {@code ipcOverride} es {@code null} usa el IPC vigente.
     * El rango {@code 0 <= ipc <= 1} se valida en el DTO de entrada.
     */
    ResultadoRenovacion renovar(Long polizaId, BigDecimal ipcOverride);

    /** Cancela la poliza y todos sus riesgos activos (RN-03, RN-07). */
    ResultadoCancelacion cancelar(Long polizaId, String motivo);

    /** Agrega un riesgo a una poliza COLECTIVA no cancelada (RN-04, RN-08, RN-12) y devuelve el riesgo con id. */
    Riesgo agregarRiesgo(Long polizaId, DatosRiesgo datos);
}
