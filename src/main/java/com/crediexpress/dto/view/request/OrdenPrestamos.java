package com.crediexpress.dto.view.request;

/** Columnas por las que se puede ordenar la lista de préstamos (una por columna de la tabla). */
public enum OrdenPrestamos {
    CLIENTE,
    /** Número de préstamo. */
    PRESTAMO,
    PAGOS,
    VENCIDAS,
    CUOTA,
    TOTAL,
    SALDO,
    PROXIMO_PAGO,
    ESTADO
}
