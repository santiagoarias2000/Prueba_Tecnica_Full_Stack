package com.prueba.polizas.repository;

/** Proyeccion: cantidad de riesgos activos por poliza. */
public interface ConteoRiesgos {

    Long getPolizaId();

    Long getCantidad();
}
