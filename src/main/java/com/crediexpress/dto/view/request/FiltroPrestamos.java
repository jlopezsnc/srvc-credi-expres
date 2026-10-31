package com.crediexpress.dto.view.request;

/** Filtros de la lista de préstamos (los chips de la pantalla). */
public enum FiltroPrestamos {
    /** Con saldo: al corriente o con atraso. */
    ACTIVOS,
    /** Con saldo y sin cuotas vencidas ("Vigentes" en Convenio). */
    AL_CORRIENTE,
    CON_ATRASO,
    LIQUIDADOS,
    EN_CONVENIO,
    /** De convenios cancelados. */
    CANCELADOS,
    TODOS
}
