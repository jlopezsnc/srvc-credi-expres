package com.crediexpress.dto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.crediexpress.util.Dinero;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una cuota del préstamo. */
@Entity
@Table(name = "estado_prestamos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstadoPrestamos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_cliente", referencedColumnName = "id_cliente")
    private Cliente idCliente;

    /** La columna es FLOAT, pero siempre guarda enteros (1, 2, 3…). */
    @Column(name = "numero_cuota")
    private Integer numeroCuota;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /** Columna FLOAT: normalizar con {@link com.crediexpress.util.Dinero#de} antes de operar. */
    @Column(name = "importe_cuota")
    private BigDecimal importeCuota;

    /** Fecha en que quedó pagada la cuota; vacía mientras tenga saldo. */
    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    /** Código de {@link EstadoCuota}. */
    @Column(name = "estado_pago")
    private Integer estadoPago;

    @Column(name = "dias_moratorios")
    private Integer diasMoratorios;

    @Column(name = "alerta")
    private String alerta;

    @ManyToOne
    @JoinColumn(name = "id_calculadora_prestamo", referencedColumnName = "id_calculadora_prestamo")
    private CalculadoraPrestamos idCalculadoraPrestamo;

    public EstadoCuota estado() {
        return EstadoCuota.deCodigo(estadoPago);
    }

    /** Lo que vale la cuota, normalizado a centavos (la columna es FLOAT). */
    public BigDecimal importe() {
        return Dinero.de(importeCuota);
    }

    /**
     * Lo que falta por pagar con lo abonado. Una cuota marcada como pagada antes de existir la tabla
     * {@code pago} no tiene abonos registrados y su saldo es cero.
     */
    public BigDecimal saldo(BigDecimal abonado) {
        if (estado() == EstadoCuota.PAGADA) {
            return Dinero.CERO;
        }
        return importe().subtract(abonado).max(Dinero.CERO);
    }

    /**
     * Deja {@code estado_pago} y {@code fecha_pago} de acuerdo con lo abonado: sin abonos queda
     * pendiente, con saldo queda en abono parcial y sin saldo queda pagada en {@code fechaPago}
     * (si es {@code null} conserva la fecha que ya tenía).
     *
     * @return el saldo que queda
     */
    public BigDecimal aplicarAbonos(BigDecimal abonado, LocalDate fechaPago) {
        BigDecimal saldo = importe().subtract(abonado).max(Dinero.CERO);
        if (saldo.signum() == 0) {
            estadoPago = EstadoCuota.PAGADA.codigo();
            if (fechaPago != null) {
                this.fechaPago = fechaPago;
            }
        } else {
            estadoPago = (abonado.signum() == 0 ? EstadoCuota.PENDIENTE : EstadoCuota.ABONO_PARCIAL).codigo();
            this.fechaPago = null;
        }
        return saldo;
    }

    public SituacionCuota situacion(LocalDate hoy) {
        return SituacionCuota.de(fechaVencimiento, hoy);
    }

    public boolean vencida(LocalDate hoy) {
        return fechaVencimiento.isBefore(hoy);
    }

    /** Días desde el vencimiento hasta hoy; 0 si todavía no vence. */
    public int diasAtraso(LocalDate hoy) {
        return SituacionCuota.diasAtraso(fechaVencimiento, hoy);
    }

    /** Días que faltan para el vencimiento; 0 si vence hoy o ya venció. */
    public int diasParaVencer(LocalDate hoy) {
        return SituacionCuota.diasParaVencer(fechaVencimiento, hoy);
    }
}
