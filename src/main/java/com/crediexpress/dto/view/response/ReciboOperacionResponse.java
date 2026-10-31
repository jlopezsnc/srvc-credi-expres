package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.TipoPago;

/**
 * Datos para reimprimir el recibo de una operación ya cobrada (un folio). Es el mismo objeto que se
 * respondió al cobrar: {@code pago} en un pago de cuota o abono, {@code liquidacion} en una
 * liquidación (el otro va en {@code null}). {@code modalidad} y {@code totalCuotas} del préstamo
 * completan el recibo de la liquidación.
 */
public record ReciboOperacionResponse(
        TipoPago tipo,
        ModalidadResumenResponse modalidad,
        Integer totalCuotas,
        PagoResponse pago,
        LiquidacionResponse liquidacion) {
}
