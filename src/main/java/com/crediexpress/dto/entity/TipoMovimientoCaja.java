package com.crediexpress.dto.entity;

/** Dinero que entra o sale de caja sin ser un pago ni un préstamo. */
public enum TipoMovimientoCaja {
    /** Capital que se pone, incluido el capital inicial. */
    APORTACION,
    /** Dinero que se saca (para uso propio o gastos). */
    RETIRO
}
