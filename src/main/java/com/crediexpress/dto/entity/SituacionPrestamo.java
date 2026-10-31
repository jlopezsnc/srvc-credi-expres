package com.crediexpress.dto.entity;

import java.math.BigDecimal;

/**
 * Situación de un préstamo respecto a "hoy". Se calcula al consultar, no se guarda. La consulta de
 * la lista ({@code PrestamoConsultaRepository}) aplica la misma regla en SQL.
 */
public enum SituacionPrestamo {
    AL_CORRIENTE,
    CON_ATRASO,
    LIQUIDADO,
    /** Su saldo pasó a un convenio (alguna cuota con {@code estado_pago = 4}). */
    EN_CONVENIO,
    /** Préstamo de un convenio cancelado (sus cuotas con {@code estado_pago = 5}). */
    CANCELADO;

    /**
     * @param saldo          lo que falta por pagar del préstamo
     * @param cuotasVencidas cuotas con saldo cuyo vencimiento ya pasó
     */
    public static SituacionPrestamo de(BigDecimal saldo, int cuotasVencidas) {
        if (saldo.signum() == 0) {
            return LIQUIDADO;
        }
        return cuotasVencidas > 0 ? CON_ATRASO : AL_CORRIENTE;
    }
}
