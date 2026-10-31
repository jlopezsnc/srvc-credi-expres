package com.crediexpress.dto.entity;

/** Lo decide el back según el saldo de la cuota, nunca el front. */
public enum TipoPago {
    CUOTA_COMPLETA,
    ABONO_PARCIAL,
    /** Renglón de una liquidación del préstamo completo; todos los renglones comparten folio. */
    LIQUIDACION
}
