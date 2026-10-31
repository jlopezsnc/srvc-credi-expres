package com.crediexpress.dto.entity;

/**
 * Cómo va un convenio: la situación de su préstamo nuevo o, si se canceló, {@code CANCELADO}. Se
 * calcula al consultar ({@code ConvenioConsultaRepository}), no se guarda.
 */
public enum SituacionConvenio {
    AL_CORRIENTE,
    CON_ATRASO,
    LIQUIDADO,
    CANCELADO
}
