package com.prueba.polizas.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prueba.polizas.config.PolizasProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConfigIpcProviderTest {

    @Test
    void devuelveElIpcConfigurado() {
        PolizasProperties props = new PolizasProperties(
                new PolizasProperties.Seguridad("123456", "x-api-key"),
                new PolizasProperties.Ipc(new BigDecimal("0.0510")),
                new PolizasProperties.Core("http://localhost:8080", "/core-mock/evento", 2000));

        assertThat(new ConfigIpcProvider(props).ipcVigente()).isEqualByComparingTo("0.0510");
    }
}
