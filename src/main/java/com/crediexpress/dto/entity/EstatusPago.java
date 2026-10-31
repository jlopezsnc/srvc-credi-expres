package com.crediexpress.dto.entity;

/** Un pago nunca se borra: se cancela y deja de contar en el saldo. */
public enum EstatusPago {
    VIGENTE,
    CANCELADO
}
