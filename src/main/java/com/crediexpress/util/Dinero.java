package com.crediexpress.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dinero siempre con 2 decimales y redondeo HALF_UP (el mismo que usa el front con Math.round
 * sobre centavos positivos). También normaliza lo que se lee de las columnas FLOAT de la base,
 * que traen decimales basura (788.57 → 788.5700073242188).
 */
public final class Dinero {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private Dinero() {
    }

    public static BigDecimal de(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA, REDONDEO);
    }
}
