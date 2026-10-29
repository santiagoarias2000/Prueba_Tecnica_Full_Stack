package com.prueba.polizas.repository;

import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.enums.EstadoPoliza;
import com.prueba.polizas.model.enums.TipoPoliza;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a polizas. */
public interface PolizaRepository extends JpaRepository<Poliza, Long> {

    /**
     * Polizas filtradas por tipo y/o estado ({@code null} = sin filtro), ordenadas por id ascendente.
     * No carga los riesgos: el conteo de activos se obtiene con
     * {@link RiesgoRepository#contarActivosPorPoliza(java.util.Collection)} para evitar N+1.
     */
    @Query("""
            select p from Poliza p
            where (:tipo is null or p.tipo = :tipo)
              and (:estado is null or p.estado = :estado)
            order by p.id asc
            """)
    List<Poliza> buscarPorFiltros(@Param("tipo") TipoPoliza tipo, @Param("estado") EstadoPoliza estado);

    /** Carga la poliza junto con sus riesgos en una sola consulta (operaciones sobre el agregado). */
    @EntityGraph(attributePaths = "riesgos")
    Optional<Poliza> findConRiesgosById(Long id);
}
