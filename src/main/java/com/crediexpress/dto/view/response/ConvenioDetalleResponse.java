package com.crediexpress.dto.view.response;

import com.crediexpress.dto.view.response.SimulacionConvenioResponse.PrestamoConvenido;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detalle de un convenio: su resumen, el plan, los préstamos que juntó (cómo estaban al convenir),
 * el calendario de su préstamo nuevo y la bitácora. {@code puedeCancelar} y
 * {@code motivoNoCancelable} los decide el back (solo se cancela uno vigente y sin pagos).
 */
public record ConvenioDetalleResponse(
        ConvenioResumenResponse convenio,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tasaInteres,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal meses,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interes,
        LocalDate fechaPrimerPago,
        String motivo,
        List<PrestamoConvenido> prestamos,
        List<PrestamoDetalleResponse.Cuota> calendario,
        long pagosRegistrados,
        boolean puedeCancelar,
        String motivoNoCancelable,
        Bitacora bitacora) {

    public record Bitacora(
            String registradoPor,
            Instant fechaRegistro,
            String ipOrigen,
            String actualizadoPor,
            Instant fechaActualizacion,
            String canceladoPor,
            Instant fechaCancelacion,
            String motivoCancelacion) {
    }
}
