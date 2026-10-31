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

    /** 1, 2, 3… ({@code INT} desde V6; antes FLOAT). */
    @Column(name = "numero_cuota")
    private Integer numeroCuota;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /** {@code DECIMAL(12,2)} desde V6; {@link #importe()} la entrega a centavos. */
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

    /** Lo que vale la cuota, a centavos. */
    public BigDecimal importe() {
        return Dinero.de(importeCuota);
    }

    /**
     * Lo que falta por pagar con lo abonado. Una cuota marcada como pagada antes de existir la tabla
     * {@code pago} no tiene abonos registrados y su saldo es cero.
     */
    public BigDecimal saldo(BigDecimal abonado) {
        if (estado() == EstadoCuota.PAGADA || enConvenio() || cancelada()) {
            return Dinero.CERO;
        }
        return importe().subtract(abonado).max(Dinero.CERO);
    }

    public boolean enConvenio() {
        return estado() == EstadoCuota.EN_CONVENIO;
    }

    public boolean cancelada() {
        return estado() == EstadoCuota.CANCELADA;
    }

    /** Se cancela el convenio que generó este préstamo: la cuota deja de cobrarse. Solo sin abonos. */
    public void cancelar() {
        if (estado() != EstadoCuota.PENDIENTE) {
            throw new IllegalStateException("La cuota " + id + " no se puede cancelar: está " + estado());
        }
        estadoPago = EstadoCuota.CANCELADA.codigo();
    }

    /**
     * Se cancela el convenio al que pasó su saldo: vuelve a cobrarse aquí, pendiente o con abono
     * parcial según lo que ya tenía abonado.
     *
     * @return el saldo que vuelve a deber
     */
    public BigDecimal regresarDeConvenio(BigDecimal abonado) {
        if (!enConvenio()) {
            throw new IllegalStateException("La cuota " + id + " no está en convenio");
        }
        estadoPago = EstadoCuota.PENDIENTE.codigo();
        return aplicarAbonos(abonado, null);
    }

    /** Su saldo pasa a un convenio: deja de cobrarse aquí. Solo para cuotas con saldo. */
    public void pasarAConvenio() {
        if (estado() == EstadoCuota.PAGADA || enConvenio()) {
            throw new IllegalStateException("La cuota " + id + " no tiene saldo que convenir");
        }
        estadoPago = EstadoCuota.EN_CONVENIO.codigo();
    }

    /**
     * Deja {@code estado_pago} y {@code fecha_pago} de acuerdo con lo abonado: sin abonos queda
     * pendiente, con saldo queda en abono parcial y sin saldo queda pagada en {@code fechaPago}
     * (si es {@code null} conserva la fecha que ya tenía).
     *
     * @return el saldo que queda
     */
    public BigDecimal aplicarAbonos(BigDecimal abonado, LocalDate fechaPago) {
        if (enConvenio() || cancelada()) {
            throw new IllegalStateException("La cuota " + id + " está " + estado() + ": no recibe abonos");
        }
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
