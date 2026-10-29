package com.prueba.polizas.service;

import com.prueba.polizas.config.PolizasProperties;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Toma el IPC de la propiedad {@code polizas.ipc.valor}. */
@Component
public class ConfigIpcProvider implements IpcProvider {

    private final PolizasProperties properties;

    public ConfigIpcProvider(PolizasProperties properties) {
        this.properties = properties;
    }

    @Override
    public BigDecimal ipcVigente() {
        return properties.ipc().valor();
    }
}
