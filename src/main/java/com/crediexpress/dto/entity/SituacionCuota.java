package com.crediexpress.dto.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Situación de una cuota con saldo respecto a "hoy". Se calcula al consultar, no se guarda. */
public enum SituacionCuota {
    VENCIDA,
    HOY,
    PROXIMA;

    public static SituacionCuota de(LocalDate fechaVencimiento, LocalDate hoy) {
        int comparacion = fechaVencimiento.compareTo(hoy);
        if (comparacion < 0) {
            return VENCIDA;
        }
        return comparacion == 0 ? HOY : PROXIMA;
    }

    /** Días desde el vencimiento hasta hoy; 0 si todavía no vence. */
    public static int diasAtraso(LocalDate fechaVencimiento, LocalDate hoy) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(fechaVencimiento, hoy));
    }

    /** Días que faltan para el vencimiento; 0 si vence hoy o ya venció. */
    public static int diasParaVencer(LocalDate fechaVencimiento, LocalDate hoy) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(hoy, fechaVencimiento));
    }
}
