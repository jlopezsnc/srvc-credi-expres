package com.crediexpress.dto.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cómo va una cuota en el calendario del préstamo. Se calcula al consultar, no se guarda. */
public enum EstatusCuota {
    PAGADA,
    /** Con saldo y ya venció (tenga o no abonos). */
    VENCIDA,
    /** Con abonos y saldo, todavía no vence. */
    ABONO_PARCIAL,
    PENDIENTE,
    /** Su saldo pasó a un convenio. */
    EN_CONVENIO,
    /** Del préstamo de un convenio cancelado. */
    CANCELADA;

    public static EstatusCuota de(EstadoCuota estado, BigDecimal abonado, BigDecimal saldo, LocalDate vencimiento,
                                  LocalDate hoy) {
        return switch (estado) {
            case EN_CONVENIO -> EN_CONVENIO;
            case CANCELADA -> CANCELADA;
            default -> de(abonado, saldo, vencimiento, hoy);
        };
    }

    public static EstatusCuota de(BigDecimal abonado, BigDecimal saldo, LocalDate vencimiento, LocalDate hoy) {
        if (saldo.signum() == 0) {
            return PAGADA;
        }
        if (vencimiento.isBefore(hoy)) {
            return VENCIDA;
        }
        return abonado.signum() > 0 ? ABONO_PARCIAL : PENDIENTE;
    }
}
