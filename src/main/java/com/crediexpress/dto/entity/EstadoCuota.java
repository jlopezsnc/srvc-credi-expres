package com.crediexpress.dto.entity;

/**
 * Valores de {@code estado_pago} acordados el 2026-10-30. "Vencida" no se guarda: es una cuota
 * pendiente o con abono cuya fecha de vencimiento ya pasó. Un {@code NULL} en la base se lee como
 * pendiente.
 */
public enum EstadoCuota {
    PENDIENTE(1),
    ABONO_PARCIAL(2),
    PAGADA(3);

    private final int codigo;

    EstadoCuota(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }
}
