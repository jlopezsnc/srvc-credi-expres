package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.EstatusMovimientoCaja;
import com.crediexpress.dto.entity.MovimientoCaja;
import com.crediexpress.dto.entity.TipoMovimientoCaja;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Una aportación o un retiro, como lo muestra la lista de movimientos de caja. */
public record MovimientoCajaResponse(
        Integer idMovimiento,
        TipoMovimientoCaja tipo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe,
        LocalDate fecha,
        String concepto,
        EstatusMovimientoCaja estatus,
        String registradoPor,
        Instant fechaRegistro,
        String canceladoPor,
        Instant fechaCancelacion,
        String motivoCancelacion) {

    public static MovimientoCajaResponse de(MovimientoCaja m) {
        return new MovimientoCajaResponse(m.getIdMovimiento(), m.getTipo(), m.getImporte(), m.getFecha(),
                m.getConcepto(), m.getEstatus(), m.getRegistradoPor(), m.getFechaRegistro(), m.getCanceladoPor(),
                m.getFechaCancelacion(), m.getMotivoCancelacion());
    }
}
