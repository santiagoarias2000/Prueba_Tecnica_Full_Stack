package com.prueba.polizas.service;

import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.RecursoNoEncontradoException;
import com.prueba.polizas.integration.core.CambioEstadoEvent;
import com.prueba.polizas.model.DatosRiesgo;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.OperacionCore;
import com.prueba.polizas.model.enums.TipoPoliza;
import com.prueba.polizas.repository.ConteoRiesgos;
import com.prueba.polizas.repository.PolizaRepository;
import com.prueba.polizas.repository.RiesgoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquesta los casos de uso de polizas: carga el agregado, invoca el dominio y publica el evento. */
@Service
public class PolizaServiceImpl implements PolizaService {

    private static final Logger log = LoggerFactory.getLogger(PolizaServiceImpl.class);

    private final PolizaRepository polizaRepository;
    private final RiesgoRepository riesgoRepository;
    private final IpcProvider ipcProvider;
    private final ApplicationEventPublisher publisher;

    public PolizaServiceImpl(PolizaRepository polizaRepository, RiesgoRepository riesgoRepository,
                             IpcProvider ipcProvider, ApplicationEventPublisher publisher) {
        this.polizaRepository = polizaRepository;
        this.riesgoRepository = riesgoRepository;
        this.ipcProvider = ipcProvider;
        this.publisher = publisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolizaConConteo> listar(TipoPoliza tipo, EstadoPoliza estado) {
        List<Poliza> polizas = polizaRepository.buscarPorFiltros(tipo, estado);
        if (polizas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = polizas.stream().map(Poliza::getId).toList();
        Map<Long, Long> conteos = riesgoRepository.contarActivosPorPoliza(ids).stream()
                .collect(Collectors.toMap(ConteoRiesgos::getPolizaId, ConteoRiesgos::getCantidad));
        return polizas.stream()
                .map(p -> new PolizaConConteo(p, conteos.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PolizaConConteo obtener(Long polizaId) {
        Poliza poliza = cargar(polizaId);
        return new PolizaConConteo(poliza, poliza.cantidadRiesgosActivos());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Riesgo> listarRiesgos(Long polizaId) {
        if (!polizaRepository.existsById(polizaId)) {
            throw polizaNoEncontrada(polizaId);
        }
        return riesgoRepository.findByPolizaIdOrderByIdAsc(polizaId);
    }

    @Override
    @Transactional
    public ResultadoRenovacion renovar(Long polizaId) {
        return renovar(polizaId, null);
    }

    @Override
    @Transactional
    public ResultadoRenovacion renovar(Long polizaId, BigDecimal ipcOverride) {
        Poliza poliza = cargar(polizaId);
        BigDecimal ipc = ipcOverride != null ? ipcOverride : ipcProvider.ipcVigente();
        poliza.renovar(ipc);
        log.info("Poliza {} renovada con IPC {}", polizaId, ipc);
        publicar(polizaId, null, OperacionCore.RENOVAR_POLIZA);
        return new ResultadoRenovacion(poliza, poliza.cantidadRiesgosActivos(), ipc);
    }

    @Override
    @Transactional
    public ResultadoCancelacion cancelar(Long polizaId, String motivo) {
        Poliza poliza = cargar(polizaId);
        int cancelados = poliza.cancelar(motivo);
        log.info("Poliza {} cancelada; riesgos cancelados: {}", polizaId, cancelados);
        publicar(polizaId, null, OperacionCore.CANCELAR_POLIZA);
        return new ResultadoCancelacion(poliza, poliza.cantidadRiesgosActivos(), cancelados);
    }

    @Override
    @Transactional
    public Riesgo agregarRiesgo(Long polizaId, DatosRiesgo datos) {
        Objects.requireNonNull(datos, "datos");
        Poliza poliza = cargar(polizaId);
        Riesgo riesgo = poliza.agregarRiesgo(datos.descripcion(), datos.arrendatario(), datos.arrendador(),
                datos.valorCanon());
        Riesgo guardado = riesgoRepository.save(riesgo);
        log.info("Riesgo {} agregado a la poliza {}", guardado.getId(), polizaId);
        publicar(polizaId, guardado.getId(), OperacionCore.AGREGAR_RIESGO);
        return guardado;
    }

    private Poliza cargar(Long polizaId) {
        return polizaRepository.findConRiesgosById(polizaId).orElseThrow(() -> polizaNoEncontrada(polizaId));
    }

    private void publicar(Long polizaId, Long riesgoId, OperacionCore operacion) {
        publisher.publishEvent(new CambioEstadoEvent(polizaId, riesgoId, operacion));
    }

    private static RecursoNoEncontradoException polizaNoEncontrada(Long polizaId) {
        return new RecursoNoEncontradoException(CodigoError.POLIZA_NO_ENCONTRADA,
                "No existe la poliza con id " + polizaId);
    }
}
