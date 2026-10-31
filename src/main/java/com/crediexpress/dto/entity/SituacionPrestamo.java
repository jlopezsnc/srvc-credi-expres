package com.crediexpress.dto.entity;

import java.math.BigDecimal;

/**
 * Situación de un préstamo respecto a "hoy". Se calcula al consultar, no se guarda. La consulta de
 * la lista ({@code PrestamoConsultaRepository}) aplica la misma regla en SQL.
 */
public enum SituacionPrestamo {
    AL_CORRIENTE,
    CON_ATRASO,
    LIQUIDADO;

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
