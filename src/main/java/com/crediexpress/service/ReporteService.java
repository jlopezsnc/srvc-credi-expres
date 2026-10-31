package com.crediexpress.service;

import com.crediexpress.dto.view.response.ReporteCarteraResponse;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse;

import java.time.LocalDate;

/** Reportes (solo lectura). Contrato: docs/API-REPORTES.md del front. */
public interface ReporteService {

    /**
     * Cobranza por fecha de registro, ambos días incluidos.
     *
     * @param desde {@code null} = día 1 del mes
     * @param hasta {@code null} = hoy
     */
    ReporteCobranzaResponse cobranza(LocalDate desde, LocalDate hasta);

    /** Cartera al día de hoy. */
    ReporteCarteraResponse cartera();
}
