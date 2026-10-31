package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.MetodoPago;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cobranza de un periodo, por fecha de registro (cuando entró el dinero a caja). Los totales,
 * {@code porDia} y {@code porMetodo} solo cuentan pagos vigentes; {@code operaciones} trae también
 * las canceladas, marcadas en {@code estatus}. Una operación es un folio: una liquidación es una sola.
 */
public record ReporteCobranzaResponse(
        LocalDate desde,
        LocalDate hasta,
        Totales totales,
        Cancelados cancelados,
        List<Dia> porDia,
        List<Metodo> porMetodo,
        List<OperacionPagoResponse> operaciones) {

    public record Totales(
            long operaciones,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recargo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecibido) {
    }

    public record Cancelados(long operaciones, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecibido) {
    }

    /** Un renglón por cada día del periodo, aunque no haya cobros. */
    public record Dia(
            LocalDate fecha,
            long operaciones,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recargo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecibido) {
    }

    /** Un renglón por cada método de pago, aunque no se haya usado. */
    public record Metodo(
            MetodoPago metodoPago,
            long operaciones,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecibido) {
    }
}
