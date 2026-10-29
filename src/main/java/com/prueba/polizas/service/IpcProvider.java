package com.prueba.polizas.service;

import java.math.BigDecimal;

/** Fuente del IPC vigente usado en la renovacion (RN-05). */
public interface IpcProvider {

    /** IPC vigente expresado como fraccion (0.0510 = 5,10 %). */
    BigDecimal ipcVigente();
}
