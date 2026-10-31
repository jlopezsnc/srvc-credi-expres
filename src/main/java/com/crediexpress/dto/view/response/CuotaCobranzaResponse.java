package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.SituacionCuota;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una cuota con saldo en las pestañas de cobranza. */
public record CuotaCobranzaResponse(
        Integer idCuota,
        Integer idPrestamo,
        ClienteResumenResponse cliente,
        ModalidadResumenResponse modalidad,
        Integer numeroCuota,
        Integer totalCuotas,
        LocalDate fechaVencimiento,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeCuota,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeAbonado,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoCuota,
        int diasAtraso,
        int diasParaVencer,
        SituacionCuota situacion) {
}
