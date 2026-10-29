package com.prueba.polizas.dto.response;

/**
 * Respuesta 202 del mock del CORE (RF-07).
 *
 * @param recibido  siempre {@code true}
 * @param mensaje   texto informativo
 * @param timestamp instante ISO-8601 con zona America/Bogota
 */
public record CoreEventoResponse(boolean recibido, String mensaje, String timestamp) {
}
