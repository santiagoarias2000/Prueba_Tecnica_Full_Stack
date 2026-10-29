package com.prueba.polizas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.EstadoRiesgo;
import com.prueba.polizas.model.enums.OperacionCore;
import com.prueba.polizas.model.enums.TipoPoliza;
import com.prueba.polizas.repository.ConteoRiesgos;
import com.prueba.polizas.repository.PolizaRepository;
import com.prueba.polizas.repository.RiesgoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PolizaServiceImpl (02g seccion B)")
class PolizaServiceImplTest {

    private static final BigDecimal IPC = new BigDecimal("0.0510");
    private static final Long ID = 7L;
    private static final Long INEXISTENTE = 999L;

    @Mock
    private PolizaRepository polizaRepository;
    @Mock
    private RiesgoRepository riesgoRepository;
    @Mock
    private IpcProvider ipcProvider;
    @Mock
    private ApplicationEventPublisher publisher;
    @Captor
    private ArgumentCaptor<CambioEstadoEvent> evento;

    private PolizaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PolizaServiceImpl(polizaRepository, riesgoRepository, ipcProvider, publisher);
    }

    private static DatosRiesgo datos(String canon) {
        return new DatosRiesgo("Apto 301 - Cra 15 # 93-20, Bogota", "Ana Gomez", "Inversiones Andes SAS",
                new BigDecimal(canon));
    }

    private static Poliza conId(Poliza poliza, Long id) {
        ReflectionTestUtils.setField(poliza, "id", id);
        return poliza;
    }

    private static Poliza individual() {
        return conId(Poliza.individual("IND-1", "Carlos Perez", LocalDate.of(2026, 1, 1), 12, datos("1500000")), ID);
    }

    private static Poliza colectiva() {
        return conId(Poliza.colectiva("COL-1", "Inmobiliaria SAS", LocalDate.of(2026, 2, 1), 6,
                List.of(datos("2000000"), datos("1200000"))), ID);
    }

    private void dadaLaPoliza(Poliza poliza) {
        when(polizaRepository.findConRiesgosById(ID)).thenReturn(Optional.of(poliza));
    }

    private CambioEstadoEvent eventoPublicado() {
        verify(publisher).publishEvent(evento.capture());
        return evento.getValue();
    }

    private static ConteoRiesgos conteo(long polizaId, long cantidad) {
        return new ConteoRiesgos() {
            @Override
            public Long getPolizaId() {
                return polizaId;
            }

            @Override
            public Long getCantidad() {
                return cantidad;
            }
        };
    }

    @Nested
    @DisplayName("S1 - poliza inexistente")
    class PolizaInexistente {

        private void assertNoEncontrada(Runnable accion) {
            assertThatThrownBy(accion::run)
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .extracting(e -> ((NegocioException) e).getCodigo())
                    .isEqualTo(CodigoError.POLIZA_NO_ENCONTRADA);
            verifyNoInteractions(publisher);
        }

        @Test
        void renovar() {
            when(polizaRepository.findConRiesgosById(INEXISTENTE)).thenReturn(Optional.empty());
            assertNoEncontrada(() -> service.renovar(INEXISTENTE));
        }

        @Test
        void cancelar() {
            when(polizaRepository.findConRiesgosById(INEXISTENTE)).thenReturn(Optional.empty());
            assertNoEncontrada(() -> service.cancelar(INEXISTENTE, "motivo"));
        }

        @Test
        void agregarRiesgo() {
            when(polizaRepository.findConRiesgosById(INEXISTENTE)).thenReturn(Optional.empty());
            assertNoEncontrada(() -> service.agregarRiesgo(INEXISTENTE, datos("1000000")));
            verify(riesgoRepository, never()).save(any());
        }

        @Test
        void obtener() {
            when(polizaRepository.findConRiesgosById(INEXISTENTE)).thenReturn(Optional.empty());
            assertNoEncontrada(() -> service.obtener(INEXISTENTE));
        }

        @Test
        void listarRiesgos() {
            when(polizaRepository.existsById(INEXISTENTE)).thenReturn(false);
            assertNoEncontrada(() -> service.listarRiesgos(INEXISTENTE));
            verify(riesgoRepository, never()).findByPolizaIdOrderByIdAsc(any());
        }
    }

    @Nested
    @DisplayName("S2 - renovar usa IpcProvider")
    class RenovarConIpc {

        @Test
        @DisplayName("Aplica el IPC vigente y lo devuelve como ipcAplicado")
        void aplicaIpcVigente() {
            dadaLaPoliza(individual());
            when(ipcProvider.ipcVigente()).thenReturn(IPC);

            ResultadoRenovacion resultado = service.renovar(ID);

            verify(ipcProvider).ipcVigente();
            assertThat(resultado.ipcAplicado()).isEqualByComparingTo(IPC);
            assertThat(resultado.cantidadRiesgosActivos()).isEqualTo(1);
            assertThat(resultado.poliza().getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
            assertThat(resultado.poliza().getValorCanon()).isEqualTo(new BigDecimal("1576500.00"));
            assertThat(resultado.poliza().getValorPrima()).isEqualTo(new BigDecimal("18918000.00"));
            assertThat(resultado.poliza().getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
        }

        @Test
        @DisplayName("Con IPC explicito no consulta el proveedor")
        void ipcOverride() {
            dadaLaPoliza(colectiva());
            BigDecimal override = new BigDecimal("0.10");

            ResultadoRenovacion resultado = service.renovar(ID, override);

            verifyNoInteractions(ipcProvider);
            assertThat(resultado.ipcAplicado()).isEqualByComparingTo(override);
            assertThat(resultado.poliza().getValorCanon()).isEqualTo(new BigDecimal("3520000.00"));
            assertThat(resultado.cantidadRiesgosActivos()).isEqualTo(2);
        }

        @Test
        @DisplayName("IPC override null usa el IPC vigente")
        void ipcOverrideNull() {
            dadaLaPoliza(colectiva());
            when(ipcProvider.ipcVigente()).thenReturn(IPC);

            ResultadoRenovacion resultado = service.renovar(ID, null);

            assertThat(resultado.poliza().getValorCanon()).isEqualTo(new BigDecimal("3363200.00"));
            assertThat(resultado.poliza().getValorPrima()).isEqualTo(new BigDecimal("20179200.00"));
        }
    }

    @Nested
    @DisplayName("S3 - cada cambio de estado publica CambioEstadoEvent con la operacion correcta")
    class PublicaEvento {

        @Test
        void renovarPublicaRenovarPoliza() {
            dadaLaPoliza(individual());
            when(ipcProvider.ipcVigente()).thenReturn(IPC);

            service.renovar(ID);

            assertThat(eventoPublicado()).isEqualTo(new CambioEstadoEvent(ID, null, OperacionCore.RENOVAR_POLIZA));
        }

        @Test
        void cancelarPublicaCancelarPoliza() {
            Poliza poliza = colectiva();
            dadaLaPoliza(poliza);

            ResultadoCancelacion resultado = service.cancelar(ID, "Solicitud del tomador");

            assertThat(eventoPublicado()).isEqualTo(new CambioEstadoEvent(ID, null, OperacionCore.CANCELAR_POLIZA));
            assertThat(resultado.riesgosCancelados()).isEqualTo(2);
            assertThat(resultado.cantidadRiesgosActivos()).isZero();
            assertThat(resultado.poliza().getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
            assertThat(poliza.getRiesgos()).allSatisfy(r -> {
                assertThat(r.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
                assertThat(r.getMotivoCancelacion()).isEqualTo("Solicitud del tomador");
            });
        }

        @Test
        void agregarRiesgoPublicaAgregarRiesgoConIdDelRiesgo() {
            Poliza poliza = colectiva();
            dadaLaPoliza(poliza);
            when(riesgoRepository.save(any(Riesgo.class))).thenAnswer(inv -> {
                Riesgo r = inv.getArgument(0);
                ReflectionTestUtils.setField(r, "id", 55L);
                return r;
            });

            Riesgo riesgo = service.agregarRiesgo(ID, datos("1800000"));

            assertThat(riesgo.getId()).isEqualTo(55L);
            assertThat(riesgo.getPoliza()).isSameAs(poliza);
            assertThat(poliza.getValorCanon()).isEqualTo(new BigDecimal("5000000.00"));
            assertThat(eventoPublicado()).isEqualTo(new CambioEstadoEvent(ID, 55L, OperacionCore.AGREGAR_RIESGO));
            verify(polizaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("S4 - operacion fallida por regla no publica evento")
    class FalloNoPublica {

        @Test
        void renovarCancelada() {
            Poliza poliza = individual();
            poliza.cancelar("x");
            dadaLaPoliza(poliza);
            when(ipcProvider.ipcVigente()).thenReturn(IPC);

            assertThatThrownBy(() -> service.renovar(ID))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting(e -> ((NegocioException) e).getCodigo())
                    .isEqualTo(CodigoError.POLIZA_CANCELADA_NO_RENOVABLE);
            verify(publisher, never()).publishEvent(any());
        }

        @Test
        void cancelarYaCancelada() {
            Poliza poliza = colectiva();
            poliza.cancelar("x");
            dadaLaPoliza(poliza);

            assertThatThrownBy(() -> service.cancelar(ID, "otra vez"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting(e -> ((NegocioException) e).getCodigo())
                    .isEqualTo(CodigoError.POLIZA_YA_CANCELADA);
            verify(publisher, never()).publishEvent(any());
        }

        @Test
        void agregarRiesgoAIndividual() {
            dadaLaPoliza(individual());

            assertThatThrownBy(() -> service.agregarRiesgo(ID, datos("1000000")))
                    .isInstanceOf(ReglaNegocioException.class)
                    .extracting(e -> ((NegocioException) e).getCodigo())
                    .isEqualTo(CodigoError.TIPO_POLIZA_NO_PERMITE_RIESGOS);
            verify(riesgoRepository, never()).save(any());
            verify(publisher, never()).publishEvent(any());
        }

        @Test
        void agregarRiesgoAColectivaCancelada() {
            Poliza poliza = colectiva();
            poliza.cancelar("x");
            dadaLaPoliza(poliza);

            assertThatThrownBy(() -> service.agregarRiesgo(ID, datos("1000000")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting(e -> ((NegocioException) e).getCodigo())
                    .isEqualTo(CodigoError.POLIZA_CANCELADA);
            verify(riesgoRepository, never()).save(any());
            verify(publisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("S5 - listar con filtros")
    class Listar {

        @Test
        @DisplayName("Pasa tipo y estado al repositorio y combina el conteo de activos (0 si no aparece)")
        void combinaFiltrosYConteo() {
            Poliza p1 = colectiva();
            Poliza p2 = conId(Poliza.colectiva("COL-2", "Otra SAS", LocalDate.of(2026, 3, 1), 12, List.of()), 8L);
            when(polizaRepository.buscarPorFiltros(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA))
                    .thenReturn(List.of(p1, p2));
            when(riesgoRepository.contarActivosPorPoliza(List.of(ID, 8L))).thenReturn(List.of(conteo(ID, 2)));

            List<PolizaConConteo> resultado = service.listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA);

            verify(polizaRepository).buscarPorFiltros(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA);
            assertThat(resultado).extracting(PolizaConConteo::poliza).containsExactly(p1, p2);
            assertThat(resultado).extracting(PolizaConConteo::cantidadRiesgosActivos).containsExactly(2L, 0L);
        }

        @Test
        @DisplayName("Filtros null = sin filtro")
        void sinFiltros() {
            when(polizaRepository.buscarPorFiltros(null, null)).thenReturn(List.of(individual()));
            when(riesgoRepository.contarActivosPorPoliza(List.of(ID))).thenReturn(List.of(conteo(ID, 1)));

            assertThat(service.listar(null, null)).singleElement()
                    .extracting(PolizaConConteo::cantidadRiesgosActivos).isEqualTo(1L);
            verify(polizaRepository).buscarPorFiltros(null, null);
        }

        @Test
        @DisplayName("Solo tipo o solo estado")
        void filtroParcial() {
            when(polizaRepository.buscarPorFiltros(TipoPoliza.INDIVIDUAL, null)).thenReturn(List.of());
            when(polizaRepository.buscarPorFiltros(null, EstadoPoliza.CANCELADA)).thenReturn(List.of());

            assertThat(service.listar(TipoPoliza.INDIVIDUAL, null)).isEmpty();
            assertThat(service.listar(null, EstadoPoliza.CANCELADA)).isEmpty();
            verify(polizaRepository).buscarPorFiltros(TipoPoliza.INDIVIDUAL, null);
            verify(polizaRepository).buscarPorFiltros(null, EstadoPoliza.CANCELADA);
            verify(riesgoRepository, never()).contarActivosPorPoliza(anyCollection());
        }
    }

    @Test
    @DisplayName("listarRiesgos devuelve los riesgos del repositorio si la poliza existe")
    void listarRiesgosExistente() {
        Poliza poliza = colectiva();
        when(polizaRepository.existsById(ID)).thenReturn(true);
        when(riesgoRepository.findByPolizaIdOrderByIdAsc(ID)).thenReturn(poliza.getRiesgos());

        assertThat(service.listarRiesgos(ID)).hasSize(2);
        verifyNoInteractions(publisher);
    }

    @Nested
    @DisplayName("Extra EX-01 - obtener detalle")
    class Obtener {

        @Test
        @DisplayName("Devuelve la poliza con su conteo de riesgos activos y no publica eventos")
        void debeDevolverPolizaConConteo() {
            Poliza poliza = colectiva();
            poliza.cancelarRiesgo(poliza.getRiesgos().get(0), "Desocupado");
            dadaLaPoliza(poliza);

            PolizaConConteo resultado = service.obtener(ID);

            assertThat(resultado.poliza()).isSameAs(poliza);
            assertThat(resultado.cantidadRiesgosActivos()).isEqualTo(1);
            verifyNoInteractions(publisher);
        }
    }
}
