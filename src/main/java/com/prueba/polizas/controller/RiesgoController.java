package com.prueba.polizas.controller;

import com.prueba.polizas.dto.request.CancelacionRequest;
import com.prueba.polizas.dto.response.RiesgoResponse;
import com.prueba.polizas.mapper.PolizaMapper;
import com.prueba.polizas.service.RiesgoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de riesgos (RF-06). */
@RestController
@RequestMapping("/riesgos")
@Tag(name = "Riesgos", description = "Operaciones sobre riesgos individuales")
public class RiesgoController {

    private final RiesgoService riesgoService;

    public RiesgoController(RiesgoService riesgoService) {
        this.riesgoService = riesgoService;
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar un riesgo (solo pólizas COLECTIVAS)")
    public RiesgoResponse cancelar(@PathVariable Long id,
                                   @RequestBody(required = false) @Valid CancelacionRequest request) {
        return PolizaMapper.aResponse(riesgoService.cancelar(id, request == null ? null : request.motivo()));
    }
}
