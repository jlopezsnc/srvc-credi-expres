package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.SituacionCuota;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que falta por pagar de un préstamo, para el diálogo "Liquidar préstamo". */
public record SaldoPrestamoResponse(
        Integer idPrestamo,
        ClienteResumenResponse cliente,
        ModalidadResumenResponse modalidad,
        Integer totalCuotas,
        int cuotasPendientes,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoTotal,
        boolean hayCuotasVencidas,
        List<Cuota> cuotas) {

    public record Cuota(
            Integer idCuota,
            Integer numeroCuota,
            LocalDate fechaVencimiento,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeCuota,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeAbonado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoCuota,
            int diasAtraso,
            int diasParaVencer,
            SituacionCuota situacion) {
    }
}
