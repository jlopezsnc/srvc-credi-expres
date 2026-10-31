package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.entity.TipoPago;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Una operación de cobro: un folio. Un pago de cuota es un renglón de {@code pago}; una liquidación
 * son varios con el mismo folio (de {@code cuotaInicial} a {@code cuotaFinal}). La usan "Cobrado hoy"
 * y el reporte de cobranza.
 */
public record OperacionPagoResponse(
        String folio,
        OffsetDateTime fechaRegistro,
        LocalDate fechaPago,
        ClienteResumenResponse cliente,
        Integer idPrestamo,
        int cuotaInicial,
        int cuotaFinal,
        Integer totalCuotas,
        TipoPago tipo,
        MetodoPago metodoPago,
        String referencia,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recargo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecibido,
        String registradoPor,
        EstatusPago estatus) {

    public boolean vigente() {
        return estatus == EstatusPago.VIGENTE;
    }
}
