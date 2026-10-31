package com.crediexpress.dto.view.request;

/** Columnas por las que se puede ordenar la lista de cuotas por cobrar. */
public enum OrdenCuotas {
    CLIENTE,
    /** Número de cuota. */
    PAGO,
    VENCE,
    /** Igual que {@link #VENCE}: la situación depende de la fecha de vencimiento. */
    SITUACION,
    SALDO
}
