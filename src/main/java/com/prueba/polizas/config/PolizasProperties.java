package com.prueba.polizas.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de la aplicacion bajo el prefijo {@code polizas}.
 *
 * @param seguridad configuracion de la API key
 * @param ipc       IPC vigente usado en la renovacion
 * @param core      destino del servicio de edicion del CORE
 */
@Validated
@ConfigurationProperties("polizas")
public record PolizasProperties(
        @Valid @NotNull Seguridad seguridad,
        @Valid @NotNull Ipc ipc,
        @Valid @NotNull Core core) {

    /**
     * @param apiKey valor esperado del header de seguridad
     * @param header nombre del header (por defecto {@code x-api-key})
     */
    public record Seguridad(@NotBlank String apiKey, @NotBlank String header) {
    }

    /**
     * @param valor IPC expresado como fraccion (0.0510 = 5,10 %)
     */
    public record Ipc(@NotNull @PositiveOrZero BigDecimal valor) {
    }

    /**
     * @param url       URL base del CORE; si esta vacia se usa el mock local
     *                  ({@code http://localhost:<puerto real del servidor>})
     * @param path      ruta del servicio de edicion
     * @param timeoutMs timeout de la llamada en milisegundos
     */
    public record Core(String url, @NotBlank String path, @Positive int timeoutMs) {
    }
}
