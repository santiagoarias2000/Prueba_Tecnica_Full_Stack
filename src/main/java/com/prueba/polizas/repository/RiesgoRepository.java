package com.prueba.polizas.repository;

import com.prueba.polizas.model.Riesgo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a riesgos. */
public interface RiesgoRepository extends JpaRepository<Riesgo, Long> {

    /** Riesgos de una poliza ordenados por id ascendente. */
    List<Riesgo> findByPolizaIdOrderByIdAsc(Long polizaId);

    /**
     * Cuenta riesgos ACTIVOS agrupados por poliza en una sola consulta (evita N+1 en {@code GET /polizas}).
     * Las polizas sin riesgos activos no aparecen en el resultado (equivale a 0).
     */
    @Query("""
            select r.poliza.id as polizaId, count(r) as cantidad
            from Riesgo r
            where r.poliza.id in :polizaIds
              and r.estado = com.prueba.polizas.model.enums.EstadoRiesgo.ACTIVO
            group by r.poliza.id
            """)
    List<ConteoRiesgos> contarActivosPorPoliza(@Param("polizaIds") Collection<Long> polizaIds);
}
