package com.prueba.polizas.controller;

import com.prueba.polizas.model.DatosRiesgo;
import com.prueba.polizas.model.Poliza;
import com.prueba.polizas.model.Riesgo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** Entidades de dominio con ids asignados, con los valores de la tabla de ejemplos de 02b / semilla 02d. */
final class Fixtures {

    static final String API_KEY_HEADER = "x-api-key";
    static final String API_KEY = "123456";

    private Fixtures() {
    }

    /** Poliza 1: individual, canon 1.500.000, 12 meses desde 2026-01-01 (riesgo id 1). */
    static Poliza polizaIndividual1() {
        Poliza poliza = Poliza.individual("IND-2026-0001", "Carlos Pérez", LocalDate.of(2026, 1, 1), 12,
                new DatosRiesgo("Casa - Calle 45 # 12-30, Medellín", "Carlos Pérez", "María Restrepo",
                        new BigDecimal("1500000.00")));
        ReflectionTestUtils.setField(poliza, "id", 1L);
        ReflectionTestUtils.setField(poliza.getRiesgos().get(0), "id", 1L);
        return poliza;
    }

    /** Poliza 2: colectiva, riesgos 2.000.000 (id 10) + 1.200.000 (id 11), 6 meses desde 2026-02-01. */
    static Poliza polizaColectiva2() {
        Poliza poliza = Poliza.colectiva("COL-2026-0001", "Inmobiliaria Los Andes SAS", LocalDate.of(2026, 2, 1), 6,
                List.of(new DatosRiesgo("Apto 301 - Cra 15 # 93-20, Bogotá", "Ana Gómez", "Inversiones Andes SAS",
                                new BigDecimal("2000000.00")),
                        new DatosRiesgo("Apto 502 - Cra 15 # 93-20, Bogotá", "Luis Martínez",
                                "Inversiones Andes SAS", new BigDecimal("1200000.00"))));
        ReflectionTestUtils.setField(poliza, "id", 2L);
        ReflectionTestUtils.setField(poliza.getRiesgos().get(0), "id", 10L);
        ReflectionTestUtils.setField(poliza.getRiesgos().get(1), "id", 11L);
        return poliza;
    }

    static Riesgo conId(Riesgo riesgo, long id) {
        ReflectionTestUtils.setField(riesgo, "id", id);
        return riesgo;
    }
}
