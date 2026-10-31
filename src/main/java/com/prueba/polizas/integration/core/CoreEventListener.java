package com.prueba.polizas.integration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifica al CORE cada {@link CambioEstadoEvent} despues del commit. Es sincrono, en el mismo hilo
 * de la peticion (el MDC con el correlationId sigue disponible). Ninguna excepcion sale de aqui: la operacion
 * local ya esta confirmada y la respuesta HTTP no depende del CORE.
 */
@Component
public class CoreEventListener {

    private static final Logger log = LoggerFactory.getLogger(CoreEventListener.class);

    private final CoreEdicionPort coreEdicionPort;

    public CoreEventListener(CoreEdicionPort coreEdicionPort) {
        this.coreEdicionPort = coreEdicionPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmarCambio(CambioEstadoEvent evento) {
        try {
            coreEdicionPort.notificar(CoreEventoPayload.desde(evento));
        } catch (RuntimeException e) {
            log.error("[CORE-LISTENER] Error inesperado notificando al CORE polizaId={} operacion={} causa={}",
                    evento.polizaId(), evento.operacion(), e.toString());
        }
    }
}
