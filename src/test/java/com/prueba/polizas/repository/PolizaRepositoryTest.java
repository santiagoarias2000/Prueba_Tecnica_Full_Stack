package com.prueba.polizas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.prueba.polizas.model.DatosRiesgo;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@DisplayName("Repositorios sobre schema.sql + data.sql")
class PolizaRepositoryTest {

    @Autowired
    private PolizaRepository polizaRepository;

    @Autowired
    private RiesgoRepository riesgoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Carga las 5 polizas semilla ordenadas por id cuando no hay filtros")
    void debeCargarSemillaSinFiltros() {
        List<Poliza> polizas = polizaRepository.buscarPorFiltros(null, null);

        assertThat(polizas).extracting(Poliza::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(polizas.get(1).getValorCanon()).isEqualByComparingTo("3200000.00");
        assertThat(polizas.get(1).getValorPrima()).isEqualByComparingTo("19200000.00");
    }

    @Test
    @DisplayName("Filtra por tipo, por estado y por ambos")
    void debeFiltrarPorTipoYEstado() {
        assertThat(polizaRepository.buscarPorFiltros(TipoPoliza.INDIVIDUAL, null))
                .extracting(Poliza::getId).containsExactly(1L, 3L, 5L);
        assertThat(polizaRepository.buscarPorFiltros(null, EstadoPoliza.RENOVADA))
                .extracting(Poliza::getId).containsExactly(4L);
        assertThat(polizaRepository.buscarPorFiltros(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA))
                .extracting(Poliza::getId).containsExactly(2L);
        assertThat(polizaRepository.buscarPorFiltros(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA)).isEmpty();
    }

    @Test
    @DisplayName("Cuenta riesgos activos por poliza en una sola consulta")
    void debeContarRiesgosActivosPorPoliza() {
        Map<Long, Long> conteo = riesgoRepository.contarActivosPorPoliza(List.of(1L, 2L, 3L, 4L, 5L)).stream()
                .collect(Collectors.toMap(ConteoRiesgos::getPolizaId, ConteoRiesgos::getCantidad));

        assertThat(conteo).containsOnly(Map.entry(1L, 1L), Map.entry(2L, 2L), Map.entry(4L, 2L), Map.entry(5L, 1L));
    }

    @Test
    @DisplayName("Lista riesgos de una poliza ordenados por id (poliza 2 -> 10, 11)")
    void debeListarRiesgosDePoliza() {
        assertThat(riesgoRepository.findByPolizaIdOrderByIdAsc(2L))
                .extracting(Riesgo::getId).containsExactly(10L, 11L);
        assertThat(riesgoRepository.findByPolizaIdOrderByIdAsc(4L)).hasSize(3);
    }

    @Test
    @DisplayName("Semilla consistente con el dominio: recalcular no cambia canon ni prima")
    void semillaDebeSerConsistente() {
        for (Long id : List.of(1L, 2L, 4L, 5L)) {
            Poliza poliza = polizaRepository.findConRiesgosById(id).orElseThrow();
            BigDecimal canon = poliza.getValorCanon();
            BigDecimal prima = poliza.getValorPrima();

            poliza.recalcularValores();

            assertThat(poliza.getValorCanon()).as("canon poliza %d", id).isEqualTo(canon);
            assertThat(poliza.getValorPrima()).as("prima poliza %d", id).isEqualTo(prima);
        }
    }

    @Test
    @DisplayName("Persiste una poliza nueva (ids desde 100, auditoria, version); el riesgo agregado obtiene id con flush")
    void debePersistirPolizaNueva() {
        Poliza poliza = Poliza.colectiva("COL-2026-0100", "Inmobiliaria Nueva SAS", LocalDate.of(2026, 5, 1), 6,
                List.of(new DatosRiesgo("Local 1", "Ana", "Luis", new BigDecimal("1000000"))));

        Poliza guardada = polizaRepository.saveAndFlush(poliza);
        Riesgo agregado = guardada.agregarRiesgo("Local 2", "Eva", "Juan", new BigDecimal("500000"));
        polizaRepository.flush();
        entityManager.clear();

        Poliza recargada = polizaRepository.findConRiesgosById(guardada.getId()).orElseThrow();
        assertThat(recargada.getId()).isGreaterThanOrEqualTo(100L);
        assertThat(agregado.getId()).isGreaterThanOrEqualTo(100L);
        assertThat(recargada.getRiesgos()).hasSize(2);
        assertThat(recargada.getValorCanon()).isEqualByComparingTo("1500000.00");
        assertThat(recargada.getVersion()).isEqualTo(1L);
        assertThat(recargada.getCreadoEn()).isNotNull();
        assertThat(recargada.getActualizadoEn()).isNotNull();
    }
}
