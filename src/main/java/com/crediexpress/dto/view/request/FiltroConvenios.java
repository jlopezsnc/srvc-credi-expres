package com.crediexpress.dto.view.request;

/** Filtros de la lista de convenios (los chips de la pantalla). */
public enum FiltroConvenios {
    /** Sin cancelar y con saldo: al corriente o con atraso. */
    VIGENTES,
    CON_ATRASO,
    LIQUIDADOS,
    CANCELADOS,
    TODOS
}
