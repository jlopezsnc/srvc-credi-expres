package com.crediexpress.dto.entity;

import java.util.Arrays;
import java.util.List;

/**
 * Valores de {@code estado_pago} acordados el 2026-10-30. "Vencida" no se guarda: es una cuota
 * pendiente o con abono cuya fecha de vencimiento ya pasó. Un {@code NULL} en la base se lee como
 * pendiente.
 */
public enum EstadoCuota {
    PENDIENTE(1),
    ABONO_PARCIAL(2),
    PAGADA(3),
    /**
     * Su saldo pasó a un convenio (V4, 2026-10-30): ya no se cobra aquí, cuenta como saldo 0 y lo
     * abonado se conserva como historial.
     */
    EN_CONVENIO(4),
    /**
     * Cuota del préstamo de un convenio cancelado (V5, 2026-10-30): no se borra, ya no se cobra y
     * cuenta como saldo 0.
     */
    CANCELADA(5);

    private static final List<Integer> CODIGOS_CON_SALDO = List.of(PENDIENTE.codigo, ABONO_PARCIAL.codigo);

    private final int codigo;

    EstadoCuota(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }

    public static EstadoCuota deCodigo(Integer codigo) {
        if (codigo == null) {
            return PENDIENTE;
        }
        return Arrays.stream(values())
                .filter(estado -> estado.codigo == codigo)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("estado_pago desconocido: " + codigo));
    }

    /** Códigos de las cuotas que todavía deben algo (además de {@code NULL}). Para las consultas. */
    public static List<Integer> codigosConSaldo() {
        return CODIGOS_CON_SALDO;
    }
}
