package com.crediexpress.dto.view.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Foto de la cartera al día de hoy: lo que se debe, cuánto está vencido y quién está atrasado. */
public record ReporteCarteraResponse(
        LocalDate fecha,
        Totales totales,
        List<Rango> antiguedad,
        List<ClienteAtraso> clientesConAtraso) {

    /**
     * @param porcentajeVencido saldo vencido entre saldo total, con un decimal ("12.5")
     */
    public record Totales(
            int prestamosActivos,
            int clientesActivos,
            int clientesConAtraso,
            int cuotasVencidas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoTotal,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoVencido,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoPorVencer,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal porcentajeVencido) {
    }

    /** Antigüedad del saldo vencido por días de atraso; {@code diasHasta} nulo = sin límite. */
    public record Rango(
            String etiqueta,
            int diasDesde,
            Integer diasHasta,
            int cuotas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldo) {
    }

    public record ClienteAtraso(
            ClienteResumenResponse cliente,
            int prestamos,
            int cuotasVencidas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoVencido,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoTotal,
            int diasMaximoAtraso,
            LocalDate vencimientoMasAntiguo) {
    }
}
