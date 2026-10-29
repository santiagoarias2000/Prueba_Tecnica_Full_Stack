package com.prueba.polizas;

import static org.assertj.core.api.Assertions.assertThat;

import com.prueba.polizas.config.PolizasProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PolizasApiApplicationTests {

    @Autowired
    private PolizasProperties properties;

    @Test
    void cargaContextoYPropiedades() {
        assertThat(properties.seguridad().apiKey()).isEqualTo("123456");
        assertThat(properties.seguridad().header()).isEqualTo("x-api-key");
        assertThat(properties.ipc().valor()).isEqualByComparingTo(new BigDecimal("0.0510"));
        assertThat(properties.core().path()).isEqualTo("/core-mock/evento");
        assertThat(properties.core().timeoutMs()).isEqualTo(2000);
    }
}
