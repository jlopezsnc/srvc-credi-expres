package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.SituacionPrestamo;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un préstamo como lo muestra la lista: todo calculado en el back con los abonos vigentes y "hoy"
 * en hora de México.
 *
 * @param proximoPago  vencimiento de la primera cuota con saldo; {@code null} si está liquidado
 * @param proximaCuota número de esa cuota; {@code null} si está liquidado
 * @param folioConvenio       convenio al que pasó su saldo; {@code null} si no está en convenio
 * @param folioConvenioOrigen convenio que generó este préstamo; {@code null} si es un préstamo normal
 */
public record PrestamoResumenResponse(
        Integer idPrestamo,
        ClienteResumenResponse cliente,
        ModalidadResumenResponse modalidad,
        int numeroCuotas,
        int cuotasPagadas,
        int cuotasVencidas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeCuota,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPagar,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoPendiente,
        LocalDate proximoPago,
        Integer proximaCuota,
        SituacionPrestamo estado,
        String folioConvenio,
        String folioConvenioOrigen) {
}
