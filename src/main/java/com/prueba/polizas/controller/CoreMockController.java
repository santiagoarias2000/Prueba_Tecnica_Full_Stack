package com.prueba.polizas.controller;

import com.prueba.polizas.dto.request.CoreEventoRequest;
import com.prueba.polizas.dto.response.CoreEventoResponse;
import com.prueba.polizas.exception.ProblemDetailFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock del servicio agnostico de edicion del CORE (capa media WebLogic), RF-07.
 * Unico proposito: registrar en logs el intento de envio. No persiste ni tiene logica.
 */
@RestController
@Tag(name = "CORE mock", description = "Simula el servicio de edición del CORE (solo registra en logs)")
public class CoreMockController {

    private static final Logger log = LoggerFactory.getLogger(CoreMockController.class);
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final String MENSAJE = "Evento registrado en logs";

    @PostMapping("/core-mock/evento")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Registrar en logs un intento de envío al CORE")
    public CoreEventoResponse registrar(@Valid @RequestBody CoreEventoRequest request) {
        log.info("[CORE-MOCK] Intento de envío al CORE -> evento={}, polizaId={}, operacion={}, riesgoId={}, correlationId={}",
                request.evento(), request.polizaId(), request.operacion(), request.riesgoId(),
                MDC.get(ProblemDetailFactory.MDC_CORRELATION_ID));
        String timestamp = OffsetDateTime.now(ZONA).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new CoreEventoResponse(true, MENSAJE, timestamp);
    }
}
