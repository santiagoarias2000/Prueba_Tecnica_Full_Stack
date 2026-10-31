package com.prueba.polizas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.EstadoInvalidoException;
import com.prueba.polizas.exception.NegocioException;
import com.prueba.polizas.exception.RecursoNoEncontradoException;
import com.prueba.polizas.exception.ReglaNegocioException;
import com.prueba.polizas.integration.core.CambioEstadoEvent;
import com.prueba.polizas.model.DatosRiesgo;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.model.enums.EstadoRiesgo;
import com.prueba.polizas.model.enums.OperacionCore;
import com.prueba.polizas.repository.RiesgoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("RiesgoServiceImpl")
class RiesgoServiceImplTest {

    private static final Long POLIZA_ID = 2L;

    @Mock
    private RiesgoRepository riesgoRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    private RiesgoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RiesgoServiceImpl(riesgoRepository, publisher);
    }

    private static DatosRiesgo datos(String canon) {
        return new DatosRiesgo("Apto 301", "Ana Gomez", "Inversiones Andes SAS", new BigDecimal(canon));
    }

    private static Poliza conIds(Poliza poliza) {
        ReflectionTestUtils.setField(poliza, "id", POLIZA_ID);
        long id = 10;
        for (Riesgo r : poliza.getRiesgos()) {
            ReflectionTestUtils.setField(r, "id", id++);
        }
        return poliza;
    }

    private static Poliza colectiva(String... canones) {
        List<DatosRiesgo> riesgos = java.util.Arrays.stream(canones).map(RiesgoServiceImplTest::datos).toList();
        return conIds(Poliza.colectiva("COL-1", "Inmobiliaria SAS", LocalDate.of(2026, 2, 1), 6, riesgos));
    }

    private Riesgo dadoElRiesgo(Poliza poliza, int indice) {
        Riesgo riesgo = poliza.getRiesgos().get(indice);
        when(riesgoRepository.findById(riesgo.getId())).thenReturn(Optional.of(riesgo));
        return riesgo;
    }

    private static CodigoError codigo(Throwable e) {
        return ((NegocioException) e).getCodigo();
    }

    @Test
    @DisplayName("cancelar riesgo publica CANCELAR_RIESGO con polizaId y riesgoId y recalcula la poliza")
    void cancelaYPublica() {
        Poliza poliza = colectiva("2000000", "1200000");
        Riesgo riesgo = dadoElRiesgo(poliza, 0);

        Riesgo resultado = service.cancelar(10L, "Terminacion del contrato");

        assertThat(resultado.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
        assertThat(resultado.getFechaCancelacion()).isEqualTo(LocalDate.now());
        assertThat(resultado.getMotivoCancelacion()).isEqualTo("Terminacion del contrato");
        assertThat(poliza.getValorCanon()).isEqualTo(new BigDecimal("1200000.00"));
        assertThat(poliza.getValorPrima()).isEqualTo(new BigDecimal("7200000.00"));
        ArgumentCaptor<CambioEstadoEvent> captor = ArgumentCaptor.forClass(CambioEstadoEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new CambioEstadoEvent(POLIZA_ID, riesgo.getId(), OperacionCore.CANCELAR_RIESGO));
    }

    @Test
    @DisplayName("cancelar el ultimo riesgo activo de una colectiva loguea WARN y no cancela la poliza")
    void ultimoRiesgoActivoLogueaWarn(CapturedOutput output) {
        Poliza poliza = colectiva("2000000");
        dadoElRiesgo(poliza, 0);

        service.cancelar(10L, null);

        assertThat(poliza.getEstado()).isNotEqualTo(com.prueba.polizas.model.enums.EstadoPoliza.CANCELADA);
        assertThat(output).contains("WARN").contains("sin riesgos activos");
        verify(publisher).publishEvent(any(CambioEstadoEvent.class));
    }

    @Test
    @DisplayName("404 RIESGO_NO_ENCONTRADO sin evento")
    void riesgoInexistente() {
        when(riesgoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(99L, null))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .extracting(RiesgoServiceImplTest::codigo)
                .isEqualTo(CodigoError.RIESGO_NO_ENCONTRADO);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("409 RIESGO_YA_CANCELADO (riesgo ya cancelado) sin evento")
    void riesgoYaCancelado() {
        Poliza poliza = colectiva("2000000", "1200000");
        Riesgo riesgo = dadoElRiesgo(poliza, 0);
        poliza.cancelarRiesgo(riesgo, "previo");

        assertThatThrownBy(() -> service.cancelar(10L, null))
                .isInstanceOf(EstadoInvalidoException.class)
                .extracting(RiesgoServiceImplTest::codigo)
                .isEqualTo(CodigoError.RIESGO_YA_CANCELADO);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("409 RIESGO_YA_CANCELADO (poliza cancelada) sin evento")
    void polizaCancelada() {
        Poliza poliza = colectiva("2000000", "1200000");
        dadoElRiesgo(poliza, 1);
        poliza.cancelar("previo");

        assertThatThrownBy(() -> service.cancelar(11L, null))
                .isInstanceOf(EstadoInvalidoException.class)
                .extracting(RiesgoServiceImplTest::codigo)
                .isEqualTo(CodigoError.RIESGO_YA_CANCELADO);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("422 RIESGO_INDIVIDUAL_NO_CANCELABLE sin evento")
    void riesgoDeIndividual() {
        Poliza poliza = conIds(Poliza.individual("IND-1", "Carlos Perez", LocalDate.of(2026, 1, 1), 12,
                datos("1500000")));
        dadoElRiesgo(poliza, 0);

        assertThatThrownBy(() -> service.cancelar(10L, null))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting(RiesgoServiceImplTest::codigo)
                .isEqualTo(CodigoError.RIESGO_INDIVIDUAL_NO_CANCELABLE);
        verify(publisher, never()).publishEvent(any());
    }
}
