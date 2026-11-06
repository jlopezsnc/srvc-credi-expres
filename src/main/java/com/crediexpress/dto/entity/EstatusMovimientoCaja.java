package com.crediexpress.dto.entity;

/** Un movimiento de caja nunca se borra: se cancela y deja de contar en el saldo. */
public enum EstatusMovimientoCaja {
    VIGENTE,
    CANCELADO
}
