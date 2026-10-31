package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.MetodoPago;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Resultado de liquidar un préstamo: un folio y un renglón de pago por cuota liquidada. */
public record LiquidacionResponse(
        String folio,
        Integer idPrestamo,
        ClienteResumenResponse cliente,
        int cuotasLiquidadas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recargo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecibido,
        LocalDate fechaPago,
        MetodoPago metodoPago,
        String referencia,
        String nota,
        List<Renglon> pagos,
        boolean prestamoLiquidado,
        boolean clienteSinAdeudos,
        String registradoPor,
        OffsetDateTime fechaRegistro) {

    public record Renglon(
            Integer idPago,
            Integer idCuota,
            Integer numeroCuota,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe) {
    }
}
