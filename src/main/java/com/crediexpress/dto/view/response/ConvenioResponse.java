package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.EstatusConvenio;

import java.time.Instant;

/**
 * Convenio generado: su folio, el préstamo nuevo con el calendario y el plan tal como se guardó,
 * con los datos de bitácora.
 */
public record ConvenioResponse(
        Integer idConvenio,
        String folio,
        Integer idPrestamoConvenio,
        ClienteResumenResponse titular,
        String motivo,
        EstatusConvenio estatus,
        String registradoPor,
        Instant fechaRegistro,
        SimulacionConvenioResponse plan) {
}
