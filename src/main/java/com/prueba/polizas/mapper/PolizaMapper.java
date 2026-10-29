package com.prueba.polizas.mapper;

import com.prueba.polizas.dto.request.AgregarRiesgoRequest;
import com.prueba.polizas.dto.response.PolizaResponse;
import com.prueba.polizas.dto.response.RiesgoResponse;
import com.prueba.polizas.model.DatosRiesgo;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.service.PolizaConConteo;
import com.prueba.polizas.service.ResultadoCancelacion;
import com.prueba.polizas.service.ResultadoRenovacion;
import java.math.BigDecimal;
import java.util.List;

/** Mapeo manual entre el dominio y los DTOs HTTP (funciones puras, sin estado). */
public final class PolizaMapper {

    private static final int ESCALA_IPC = 4;

    private PolizaMapper() {
    }

    public static PolizaResponse aResponse(PolizaConConteo item) {
        return aResponse(item.poliza(), item.cantidadRiesgosActivos(), null, null);
    }

    public static List<PolizaResponse> aResponses(List<PolizaConConteo> items) {
        return items.stream().map(PolizaMapper::aResponse).toList();
    }

    public static PolizaResponse aResponse(ResultadoRenovacion resultado) {
        return aResponse(resultado.poliza(), resultado.cantidadRiesgosActivos(),
                escalaIpc(resultado.ipcAplicado()), null);
    }

    public static PolizaResponse aResponse(ResultadoCancelacion resultado) {
        return aResponse(resultado.poliza(), resultado.cantidadRiesgosActivos(), null,
                resultado.riesgosCancelados());
    }

    public static RiesgoResponse aResponse(Riesgo riesgo) {
        return new RiesgoResponse(
                riesgo.getId(),
                riesgo.getPoliza().getId(),
                riesgo.getDescripcion(),
                riesgo.getArrendatario(),
                riesgo.getArrendador(),
                riesgo.getValorCanon(),
                riesgo.getEstado(),
                riesgo.getFechaInicio(),
                riesgo.getFechaCancelacion());
    }

    public static List<RiesgoResponse> aRiesgoResponses(List<Riesgo> riesgos) {
        return riesgos.stream().map(PolizaMapper::aResponse).toList();
    }

    public static DatosRiesgo aDatos(AgregarRiesgoRequest request) {
        return new DatosRiesgo(request.descripcion().trim(), request.arrendatario().trim(),
                request.arrendador().trim(), request.valorCanon());
    }

    /** Expone el IPC con al menos 4 decimales (0.051 -> 0.0510) sin redondear valores mas precisos. */
    static BigDecimal escalaIpc(BigDecimal ipc) {
        return ipc == null || ipc.scale() >= ESCALA_IPC ? ipc : ipc.setScale(ESCALA_IPC);
    }

    private static PolizaResponse aResponse(Poliza poliza, long riesgosActivos, BigDecimal ipcAplicado,
                                            Integer riesgosCancelados) {
        return new PolizaResponse(
                poliza.getId(),
                poliza.getNumeroPoliza(),
                poliza.getTipo(),
                poliza.getEstado(),
                poliza.getTomador(),
                poliza.getFechaInicioVigencia(),
                poliza.getFechaFinVigencia(),
                poliza.getMesesVigencia(),
                poliza.getValorCanon(),
                poliza.getValorPrima(),
                riesgosActivos,
                ipcAplicado,
                riesgosCancelados);
    }
}
