package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.EstatusCuota;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Calendario de un préstamo: su resumen y cada cuota con lo abonado, su saldo y su estatus. */
public record PrestamoDetalleResponse(PrestamoResumenResponse prestamo, List<Cuota> cuotas) {

    public record Cuota(
            Integer idCuota,
            int numeroCuota,
            LocalDate fechaVencimiento,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal abonado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldo,
            LocalDate fechaPago,
            EstatusCuota estatus) {
    }
}
