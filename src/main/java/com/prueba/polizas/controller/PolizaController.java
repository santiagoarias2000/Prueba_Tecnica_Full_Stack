package com.prueba.polizas.controller;

import com.prueba.polizas.dto.request.AgregarRiesgoRequest;
import com.prueba.polizas.dto.request.CancelacionRequest;
import com.prueba.polizas.dto.request.RenovacionRequest;
import com.prueba.polizas.dto.response.PolizaResponse;
import com.prueba.polizas.dto.response.RiesgoResponse;
import com.prueba.polizas.mapper.PolizaMapper;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import com.prueba.polizas.service.PolizaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de polizas (RF-01..RF-05 y extra EX-01). */
@RestController
@RequestMapping("/polizas")
@Tag(name = "Pólizas", description = "Consulta, renovación, cancelación y riesgos de pólizas")
public class PolizaController {

    private final PolizaService polizaService;

    public PolizaController(PolizaService polizaService) {
        this.polizaService = polizaService;
    }

    @GetMapping
    @Operation(summary = "Listar pólizas por tipo y estado (filtros opcionales, case-insensitive)")
    public List<PolizaResponse> listar(
            @Parameter(description = "INDIVIDUAL | COLECTIVA") @RequestParam(required = false) TipoPoliza tipo,
            @Parameter(description = "ACTIVA | RENOVADA | CANCELADA") @RequestParam(required = false)
            EstadoPoliza estado) {
        return PolizaMapper.aResponses(polizaService.listar(tipo, estado));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Extra EX-01: detalle de una póliza (mismo contrato que RF-01)")
    public PolizaResponse obtener(@PathVariable Long id) {
        return PolizaMapper.aResponse(polizaService.obtener(id));
    }

    @GetMapping("/{id}/riesgos")
    @Operation(summary = "Listar los riesgos de una póliza")
    public List<RiesgoResponse> listarRiesgos(@PathVariable Long id) {
        return PolizaMapper.aRiesgoResponses(polizaService.listarRiesgos(id));
    }

    @PostMapping("/{id}/renovar")
    @Operation(summary = "Renovar la póliza aplicando IPC (body opcional { \"ipc\": 0.0510 })")
    public PolizaResponse renovar(@PathVariable Long id,
                                  @RequestBody(required = false) @Valid RenovacionRequest request) {
        return PolizaMapper.aResponse(polizaService.renovar(id, request == null ? null : request.ipc()));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar la póliza y todos sus riesgos")
    public PolizaResponse cancelar(@PathVariable Long id,
                                   @RequestBody(required = false) @Valid CancelacionRequest request) {
        return PolizaMapper.aResponse(polizaService.cancelar(id, request == null ? null : request.motivo()));
    }

    @PostMapping("/{id}/riesgos")
    @Operation(summary = "Agregar un riesgo (solo pólizas COLECTIVAS)")
    public ResponseEntity<RiesgoResponse> agregarRiesgo(@PathVariable Long id,
                                                        @RequestBody @Valid AgregarRiesgoRequest request) {
        Riesgo riesgo = polizaService.agregarRiesgo(id, PolizaMapper.aDatos(request));
        return ResponseEntity.created(URI.create("/riesgos/" + riesgo.getId()))
                .body(PolizaMapper.aResponse(riesgo));
    }
}
