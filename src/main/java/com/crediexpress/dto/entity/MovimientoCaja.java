package com.crediexpress.dto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Aportación de capital o retiro (tabla {@code movimiento_caja}, V9). Se crea con el builder y
 * después solo cambia por {@link #cancelar}. Nunca se borra.
 */
@Entity
@Table(name = "movimiento_caja")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false)
    private TipoMovimientoCaja tipo;

    @Column(name = "importe", nullable = false, updatable = false)
    private BigDecimal importe;

    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDate fecha;

    @Column(name = "concepto", nullable = false, updatable = false)
    private String concepto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    @Builder.Default
    private EstatusMovimientoCaja estatus = EstatusMovimientoCaja.VIGENTE;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private String registradoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    @Column(name = "cancelado_por")
    private String canceladoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_cancelacion")
    private Instant fechaCancelacion;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    public boolean vigente() {
        return estatus == EstatusMovimientoCaja.VIGENTE;
    }

    public void cancelar(String usuario, Instant cuando, String motivo) {
        this.estatus = EstatusMovimientoCaja.CANCELADO;
        this.canceladoPor = usuario;
        this.fechaCancelacion = cuando;
        this.motivoCancelacion = motivo;
    }
}
