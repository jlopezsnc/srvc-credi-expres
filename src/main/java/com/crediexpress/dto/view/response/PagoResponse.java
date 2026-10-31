package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.entity.TipoPago;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Un pago tal como lo muestran el recibo y la pestaña "Cobrado hoy". */
public record PagoResponse(
        Integer idPago,
        String folio,
        Integer idCuota,
        Integer idPrestamo,
        ClienteResumenResponse cliente,
        Integer numeroCuota,
        Integer totalCuotas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recargo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecibido,
        LocalDate fechaPago,
        MetodoPago metodoPago,
        String referencia,
        String nota,
        TipoPago tipo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoCuota,
        boolean cuotaLiquidada,
        boolean prestamoLiquidado,
        String registradoPor,
        OffsetDateTime fechaRegistro,
        EstatusPago estatus,
        String canceladoPor,
        OffsetDateTime fechaCancelacion,
        String motivoCancelacion) {
}
