package com.crediexpress.dto.entity;

/** Cómo pagó el cliente. Fuera de {@link #EFECTIVO} se exige una referencia. */
public enum MetodoPago {
    EFECTIVO,
    TRANSFERENCIA,
    DEPOSITO;

    public boolean requiereReferencia() {
        return this != EFECTIVO;
    }
}
