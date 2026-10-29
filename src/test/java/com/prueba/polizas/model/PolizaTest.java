package com.prueba.polizas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prueba.polizas.exception.CodigoError;
import com.prueba.polizas.exception.EstadoInvalidoException;
import com.prueba.polizas.exception.RecursoNoEncontradoException;
import com.prueba.polizas.exception.ReglaNegocioException;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.EstadoRiesgo;
import com.prueba.polizas.model.enums.TipoPoliza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Poliza - reglas de dominio (02g seccion A)")
class PolizaTest {

    private static final BigDecimal IPC = new BigDecimal("0.0510");
    private static final LocalDate INICIO_2026 = LocalDate.of(2026, 1, 1);

    private static BigDecimal monto(String valor) {
        return new BigDecimal(valor);
    }

    private static DatosRiesgo datos(String canon) {
        return new DatosRiesgo("Apto 101 - Calle 1 # 2-3, Bogota", "Arrendatario", "Arrendador", monto(canon));
    }

    private static Poliza individual(String canon) {
        return Poliza.individual("IND-TEST-0001", "Carlos Perez", INICIO_2026, 12, datos(canon));
    }

    private static Poliza colectiva(int meses, String... canones) {
        List<DatosRiesgo> riesgos = Arrays.stream(canones).map(PolizaTest::datos).toList();
        return Poliza.colectiva("COL-TEST-0001", "Inmobiliaria Los Andes SAS", LocalDate.of(2026, 2, 1), meses, riesgos);
    }

    @Nested
    @DisplayName("Creacion")
    class Creacion {

        @Test
        @DisplayName("Individual: canon = canon del riesgo, prima = canon x meses y vigencia de 12 meses")
        void debeCrearIndividualConValoresCalculados() {
            Poliza poliza = individual("1500000");

            assertThat(poliza.getTipo()).isEqualTo(TipoPoliza.INDIVIDUAL);
            assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
            assertThat(poliza.getValorCanon()).isEqualTo(monto("1500000.00"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("18000000.00"));
            assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(poliza.getRiesgos()).singleElement().satisfies(r -> {
                assertThat(r.estaActivo()).isTrue();
                assertThat(r.getFechaInicio()).isEqualTo(INICIO_2026);
                assertThat(r.getPoliza()).isSameAs(poliza);
            });
            assertThat(poliza.cantidadRiesgosActivos()).isEqualTo(1);
        }

        @Test
        @DisplayName("D11 - Individual con 2 riesgos es una construccion invalida (RN-01)")
        void debeRechazarIndividualConDosRiesgos() {
            List<DatosRiesgo> dosRiesgos = List.of(datos("1000000"), datos("2000000"));

            assertThatThrownBy(() -> Poliza.nueva("IND-TEST-0002", TipoPoliza.INDIVIDUAL, "Carlos Perez",
                    INICIO_2026, 12, dosRiesgos))
                    .isInstanceOf(ReglaNegocioException.class)
                    .extracting("codigo").isEqualTo(CodigoError.POLIZA_INDIVIDUAL_UN_RIESGO);
        }

        @Test
        @DisplayName("Individual sin riesgo tambien viola RN-01")
        void debeRechazarIndividualSinRiesgo() {
            assertThatThrownBy(() -> Poliza.nueva("IND-TEST-0003", TipoPoliza.INDIVIDUAL, "Carlos Perez",
                    INICIO_2026, 12, List.of()))
                    .isInstanceOf(ReglaNegocioException.class)
                    .extracting("codigo").isEqualTo(CodigoError.POLIZA_INDIVIDUAL_UN_RIESGO);
        }

        @Test
        @DisplayName("Colectiva: canon = suma de riesgos y la coleccion expuesta es inmodificable")
        void debeCrearColectivaConSumaDeRiesgos() {
            Poliza poliza = colectiva(6, "2000000", "1200000");

            assertThat(poliza.getValorCanon()).isEqualTo(monto("3200000.00"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("19200000.00"));
            assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2026, 7, 31));
            assertThatThrownBy(() -> poliza.getRiesgos().clear()).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Meses de vigencia deben ser positivos y el canon del riesgo mayor que cero")
        void debeRechazarDatosInvalidos() {
            assertThatThrownBy(() -> Poliza.individual("IND-X", "T", INICIO_2026, 0, datos("100")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> individual("0"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Renovacion")
    class Renovacion {

        @Test
        @DisplayName("D1 - Renovar individual 1.500.000 x 12 meses con IPC 5,10 %")
        void debeRenovarIndividualAjustandoCanonPrimaYVigencia() {
            Poliza poliza = individual("1500000.00");

            poliza.renovar(IPC);

            assertThat(poliza.getValorCanon()).isEqualTo(monto("1576500.00"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("18918000.00"));
            assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
            assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2027, 12, 31));
            assertThat(poliza.getRiesgos().get(0).getValorCanon()).isEqualTo(monto("1576500.00"));
        }

        @Test
        @DisplayName("D2 - Renovar colectiva 2.000.000 + 1.200.000 x 6 meses")
        void debeRenovarColectivaAjustandoCadaRiesgo() {
            Poliza poliza = colectiva(6, "2000000.00", "1200000.00");

            poliza.renovar(IPC);

            assertThat(poliza.getRiesgos()).extracting(Riesgo::getValorCanon)
                    .containsExactly(monto("2102000.00"), monto("1261200.00"));
            assertThat(poliza.getValorCanon()).isEqualTo(monto("3363200.00"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("20179200.00"));
            assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2027, 1, 31));
        }

        @Test
        @DisplayName("D3 - Redondeo HALF_UP: 1.234.567,89 x 1,051 = 1.297.530,85 y prima 15.570.370,20")
        void debeRedondearHalfUpADosDecimales() {
            Poliza poliza = individual("1234567.89");

            poliza.renovar(IPC);

            assertThat(poliza.getValorCanon()).isEqualTo(monto("1297530.85"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("15570370.20"));
        }

        @Test
        @DisplayName("D4 - No se puede renovar una poliza CANCELADA (RN-02)")
        void debeRechazarRenovacionCuandoPolizaEstaCancelada() {
            Poliza poliza = individual("1500000");
            poliza.cancelar("Solicitud del tomador");

            assertThatThrownBy(() -> poliza.renovar(IPC))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting("codigo").isEqualTo(CodigoError.POLIZA_CANCELADA_NO_RENOVABLE);
        }

        @Test
        @DisplayName("D5 - Se puede renovar una poliza RENOVADA (RN-06)")
        void debePermitirRenovarPolizaRenovada() {
            Poliza poliza = individual("1500000.00");
            poliza.renovar(IPC);

            poliza.renovar(IPC);

            assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
            assertThat(poliza.getValorCanon()).isEqualTo(monto("1656901.50"));
            assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
            assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2028, 12, 31));
        }

        @Test
        @DisplayName("Renovar colectiva no ajusta los riesgos cancelados (S9)")
        void debeAjustarSoloRiesgosActivos() {
            Poliza poliza = colectiva(6, "2000000.00", "1200000.00");
            Riesgo cancelado = poliza.getRiesgos().get(1);
            poliza.cancelarRiesgo(cancelado, "Terminacion");

            poliza.renovar(IPC);

            assertThat(cancelado.getValorCanon()).isEqualTo(monto("1200000.00"));
            assertThat(poliza.getValorCanon()).isEqualTo(monto("2102000.00"));
        }

        @Test
        @DisplayName("El IPC es obligatorio")
        void debeExigirIpc() {
            Poliza poliza = individual("1500000");

            assertThatThrownBy(() -> poliza.renovar(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Cancelacion de poliza")
    class CancelacionPoliza {

        @Test
        @DisplayName("D6 - Cancelar colectiva con 3 riesgos activos cancela todos y retorna 3 (RN-03)")
        void debeCancelarTodosLosRiesgosAlCancelarPoliza() {
            Poliza poliza = colectiva(12, "1000000", "2000000", "3000000");

            int cancelados = poliza.cancelar("Solicitud del tomador");

            assertThat(cancelados).isEqualTo(3);
            assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
            assertThat(poliza.getRiesgos()).allSatisfy(r -> {
                assertThat(r.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
                assertThat(r.getFechaCancelacion()).isEqualTo(LocalDate.now());
                assertThat(r.getMotivoCancelacion()).isEqualTo("Solicitud del tomador");
            });
            assertThat(poliza.cantidadRiesgosActivos()).isZero();
        }

        @Test
        @DisplayName("Cancelar solo cuenta los riesgos que estaban activos")
        void debeRetornarSoloRiesgosActivosCancelados() {
            Poliza poliza = colectiva(12, "1000000", "2000000", "3000000");
            poliza.cancelarRiesgo(poliza.getRiesgos().get(0), "Terminacion");

            assertThat(poliza.cancelar(null)).isEqualTo(2);
        }

        @Test
        @DisplayName("D7 - Cancelar una poliza ya CANCELADA (RN-07)")
        void debeRechazarCancelarPolizaYaCancelada() {
            Poliza poliza = individual("1500000");
            poliza.cancelar("Primera");

            assertThatThrownBy(() -> poliza.cancelar("Segunda"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting("codigo").isEqualTo(CodigoError.POLIZA_YA_CANCELADA);
        }
    }

    @Nested
    @DisplayName("Agregar riesgo")
    class AgregarRiesgo {

        @Test
        @DisplayName("D8 - Agregar riesgo a INDIVIDUAL no esta permitido (RN-04)")
        void debeRechazarAgregarRiesgoAIndividual() {
            Poliza poliza = individual("1500000");

            assertThatThrownBy(() -> poliza.agregarRiesgo("Casa 2", "Ana", "Luis", monto("900000")))
                    .isInstanceOf(ReglaNegocioException.class)
                    .extracting("codigo").isEqualTo(CodigoError.TIPO_POLIZA_NO_PERMITE_RIESGOS);
            assertThat(poliza.getRiesgos()).hasSize(1);
        }

        @Test
        @DisplayName("D9 - Agregar riesgo a COLECTIVA activa recalcula canon y prima (RN-12)")
        void debeAgregarRiesgoAColectivaYRecalcular() {
            Poliza poliza = colectiva(6, "2000000.00", "1200000.00");

            Riesgo nuevo = poliza.agregarRiesgo("Apto 502", "Pedro Ruiz", "Maria Diaz", monto("1500000.555"));

            assertThat(nuevo.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
            assertThat(nuevo.getPoliza()).isSameAs(poliza);
            assertThat(nuevo.getValorCanon()).isEqualTo(monto("1500000.56"));
            assertThat(nuevo.getFechaInicio()).isEqualTo(LocalDate.now());
            assertThat(poliza.getRiesgos()).hasSize(3).contains(nuevo);
            assertThat(poliza.getValorCanon()).isEqualTo(monto("4700000.56"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("28200003.36"));
            assertThat(poliza.cantidadRiesgosActivos()).isEqualTo(3);
        }

        @Test
        @DisplayName("Riesgo nuevo en poliza con vigencia futura inicia con la vigencia")
        void debeIniciarRiesgoNuevoEnFechaInicioVigenciaSiEsFutura() {
            LocalDate futura = LocalDate.now().plusMonths(2);
            Poliza poliza = Poliza.colectiva("COL-FUT", "Inmobiliaria", futura, 6, List.of(datos("100000")));

            Riesgo nuevo = poliza.agregarRiesgo("Local 1", "A", "B", monto("200000"));

            assertThat(nuevo.getFechaInicio()).isEqualTo(futura);
        }

        @Test
        @DisplayName("D10 - Agregar riesgo a COLECTIVA cancelada (RN-08)")
        void debeRechazarAgregarRiesgoAColectivaCancelada() {
            Poliza poliza = colectiva(6, "2000000");
            poliza.cancelar("Cierre");

            assertThatThrownBy(() -> poliza.agregarRiesgo("Apto 1", "A", "B", monto("100000")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting("codigo").isEqualTo(CodigoError.POLIZA_CANCELADA);
        }
    }

    @Nested
    @DisplayName("Cancelacion de riesgo")
    class CancelacionRiesgo {

        @Test
        @DisplayName("D12 - Cancelar riesgo de colectiva: canon de poliza = suma de los restantes (RN-12)")
        void debeCancelarRiesgoDeColectivaYRecalcular() {
            Poliza poliza = colectiva(6, "2000000.00", "1200000.00");
            Riesgo riesgo = poliza.getRiesgos().get(0);

            poliza.cancelarRiesgo(riesgo, "Terminacion del contrato");

            assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
            assertThat(riesgo.estaActivo()).isFalse();
            assertThat(riesgo.getFechaCancelacion()).isEqualTo(LocalDate.now());
            assertThat(riesgo.getMotivoCancelacion()).isEqualTo("Terminacion del contrato");
            assertThat(poliza.getValorCanon()).isEqualTo(monto("1200000.00"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("7200000.00"));
            assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
        }

        @Test
        @DisplayName("Cancelar el ultimo riesgo activo no cancela la poliza (S7)")
        void debeMantenerPolizaActivaAlCancelarUltimoRiesgo() {
            Poliza poliza = colectiva(6, "2000000.00");

            poliza.cancelarRiesgo(poliza.getRiesgos().get(0), null);

            assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
            assertThat(poliza.getValorCanon()).isEqualTo(monto("0.00"));
            assertThat(poliza.getValorPrima()).isEqualTo(monto("0.00"));
        }

        @Test
        @DisplayName("D13 - Cancelar riesgo ya CANCELADO (RN-09)")
        void debeRechazarCancelarRiesgoYaCancelado() {
            Poliza poliza = colectiva(6, "2000000", "1200000");
            Riesgo riesgo = poliza.getRiesgos().get(0);
            poliza.cancelarRiesgo(riesgo, "Primera");

            assertThatThrownBy(() -> poliza.cancelarRiesgo(riesgo, "Segunda"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting("codigo").isEqualTo(CodigoError.RIESGO_YA_CANCELADO);
        }

        @Test
        @DisplayName("D14 - Cancelar riesgo de INDIVIDUAL de forma aislada (RN-10)")
        void debeRechazarCancelarRiesgoDeIndividual() {
            Poliza poliza = individual("1500000");
            Riesgo riesgo = poliza.getRiesgos().get(0);

            assertThatThrownBy(() -> poliza.cancelarRiesgo(riesgo, "Motivo"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .extracting("codigo").isEqualTo(CodigoError.RIESGO_INDIVIDUAL_NO_CANCELABLE);
            assertThat(riesgo.estaActivo()).isTrue();
        }

        @Test
        @DisplayName("Cancelar riesgo de una poliza CANCELADA responde RIESGO_YA_CANCELADO (RN-11)")
        void debeRechazarCancelarRiesgoDePolizaCancelada() {
            Poliza poliza = individual("1500000");
            Riesgo riesgo = poliza.getRiesgos().get(0);
            poliza.cancelar("Cierre");

            assertThatThrownBy(() -> poliza.cancelarRiesgo(riesgo, "Motivo"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .extracting("codigo").isEqualTo(CodigoError.RIESGO_YA_CANCELADO);
        }

        @Test
        @DisplayName("No se cancela un riesgo que pertenece a otra poliza")
        void debeRechazarRiesgoDeOtraPoliza() {
            Poliza poliza = colectiva(6, "2000000");
            Poliza otra = colectiva(6, "1000000");
            Riesgo ajeno = otra.getRiesgos().get(0);

            assertThatThrownBy(() -> poliza.cancelarRiesgo(ajeno, "Motivo"))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .extracting("codigo").isEqualTo(CodigoError.RIESGO_NO_ENCONTRADO);
            assertThat(ajeno.estaActivo()).isTrue();
        }
    }
}
