package com.crediexpress.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Dinero siempre con 2 decimales y redondeo HALF_UP (el mismo que usa el front con Math.round
 * sobre centavos positivos). También normaliza lo que se lee de las columnas FLOAT de la base,
 * que traen decimales basura (788.57 → 788.5700073242188).
 */
public final class Dinero {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA);

    private static final Locale MEXICO = Locale.forLanguageTag("es-MX");

    private Dinero() {
    }

    public static BigDecimal de(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA, REDONDEO);
    }

    /** Para sumas que la base devuelve como {@code Double} o {@code BigDecimal}; {@code null} = 0. */
    public static BigDecimal de(Number valor) {
        if (valor == null) {
            return CERO;
        }
        return valor instanceof BigDecimal decimal ? de(decimal) : de(new BigDecimal(valor.toString()));
    }

    /** {@code null} → 0.00. */
    public static BigDecimal oCero(BigDecimal valor) {
        return valor == null ? CERO : de(valor);
    }

    /** Para mensajes al usuario: 6260 → "$6,260.00". */
    public static String texto(BigDecimal valor) {
        // DecimalFormat no es thread-safe: una instancia por llamada.
        DecimalFormat formato = new DecimalFormat("$#,##0.00", DecimalFormatSymbols.getInstance(MEXICO));
        formato.setRoundingMode(REDONDEO);
        return formato.format(valor);
    }
}
