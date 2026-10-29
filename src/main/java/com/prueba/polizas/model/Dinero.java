package com.prueba.polizas.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Reglas de redondeo monetario: escala 2, {@link RoundingMode#HALF_UP}. */
final class Dinero {

    static final int ESCALA = 2;
    static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);

    private Dinero() {
    }

    static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    static BigDecimal ajustarPorIpc(BigDecimal valor, BigDecimal ipc) {
        Objects.requireNonNull(ipc, "ipc");
        return redondear(valor.multiply(BigDecimal.ONE.add(ipc)));
    }

    static BigDecimal multiplicar(BigDecimal valor, int factor) {
        return redondear(valor.multiply(BigDecimal.valueOf(factor)));
    }
}
