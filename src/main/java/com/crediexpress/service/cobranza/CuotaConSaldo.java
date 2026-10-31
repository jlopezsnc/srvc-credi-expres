package com.crediexpress.service.cobranza;

import com.crediexpress.dto.entity.EstadoPrestamos;

import java.math.BigDecimal;

/** Una cuota con lo abonado (pagos vigentes) y lo que falta por pagar. */
public record CuotaConSaldo(EstadoPrestamos cuota, BigDecimal abonado, BigDecimal saldo) {
}
