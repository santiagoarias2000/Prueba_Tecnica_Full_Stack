package com.prueba.polizas.integration.core;

/**
 * Puerto de salida hacia el servicio agnostico de edicion del CORE.
 * Las implementaciones no deben propagar fallos de comunicacion (RN-15).
 */
public interface CoreEdicionPort {

    void notificar(CoreEventoPayload payload);
}
