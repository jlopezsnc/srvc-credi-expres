package com.crediexpress.service;

import com.crediexpress.dto.view.response.InicioResponse;

import java.time.YearMonth;

/** Pantalla de Inicio (solo lectura). Contrato: docs/API-INICIO.md del front. */
public interface InicioService {

    /** @param mes el mes del resumen; {@code null} = el mes actual en hora de México */
    InicioResponse resumen(YearMonth mes);
}
