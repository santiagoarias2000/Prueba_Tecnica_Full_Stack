package com.prueba.polizas.service;

import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.RecursoNoEncontradoException;
import com.prueba.polizas.integration.core.CambioEstadoEvent;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.model.enums.OperacionCore;
import com.prueba.polizas.model.enums.TipoPoliza;
import com.prueba.polizas.repository.RiesgoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquesta la cancelacion de riesgos a traves del agregado {@link Poliza}. */
@Service
public class RiesgoServiceImpl implements RiesgoService {

    private static final Logger log = LoggerFactory.getLogger(RiesgoServiceImpl.class);

    private final RiesgoRepository riesgoRepository;
    private final ApplicationEventPublisher publisher;

    public RiesgoServiceImpl(RiesgoRepository riesgoRepository, ApplicationEventPublisher publisher) {
        this.riesgoRepository = riesgoRepository;
        this.publisher = publisher;
    }

    @Override
    @Transactional
    public Riesgo cancelar(Long riesgoId, String motivo) {
        Riesgo riesgo = riesgoRepository.findById(riesgoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(CodigoError.RIESGO_NO_ENCONTRADO,
                        "No existe el riesgo con id " + riesgoId));
        Poliza poliza = riesgo.getPoliza();
        poliza.cancelarRiesgo(riesgo, motivo);
        log.info("Riesgo {} de la poliza {} cancelado", riesgoId, poliza.getId());
        if (poliza.getTipo() == TipoPoliza.COLECTIVA && poliza.cantidadRiesgosActivos() == 0) {
            log.warn("La poliza colectiva {} quedo sin riesgos activos; no se cancela automaticamente",
                    poliza.getId());
        }
        publisher.publishEvent(new CambioEstadoEvent(poliza.getId(), riesgoId, OperacionCore.CANCELAR_RIESGO));
        return riesgo;
    }
}
