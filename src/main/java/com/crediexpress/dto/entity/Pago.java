package com.crediexpress.dto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Un pago aplicado a una cuota (tabla {@code pago}, V3). No tiene setters: se crea con el builder y
 * después solo cambia por {@link #asignarFolio()} y {@link #cancelar}. Nunca se borra.
 */
@Entity
@Table(name = "pago")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pago {

    private static final String FORMATO_FOLIO = "P-%06d";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Integer idPago;

    @Column(name = "folio")
    private String folio;

    @Column(name = "llave_idempotencia", nullable = false, updatable = false)
    private String llaveIdempotencia;

    @Column(name = "hash_peticion", nullable = false, updatable = false)
    private String hashPeticion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuota", nullable = false, updatable = false)
    private EstadoPrestamos cuota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prestamo", nullable = false, updatable = false)
    private CalculadoraPrestamos prestamo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false, updatable = false)
    private Cliente cliente;

    @Column(name = "importe", nullable = false, updatable = false)
    private BigDecimal importe;

    @Column(name = "recargo", nullable = false, updatable = false)
    private BigDecimal recargo;

    @Column(name = "fecha_pago", nullable = false, updatable = false)
    private LocalDate fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, updatable = false)
    private MetodoPago metodoPago;

    @Column(name = "referencia", updatable = false)
    private String referencia;

    @Column(name = "nota", updatable = false)
    private String nota;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false)
    private TipoPago tipo;

    @Column(name = "saldo_cuota", nullable = false, updatable = false)
    private BigDecimal saldoCuota;

    @Column(name = "prestamo_liquidado", nullable = false, updatable = false)
    private boolean prestamoLiquidado;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private String registradoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    @Builder.Default
    private EstatusPago estatus = EstatusPago.VIGENTE;

    @Column(name = "cancelado_por")
    private String canceladoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_cancelacion")
    private Instant fechaCancelacion;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    /** Folio a partir del id generado. Solo para el primer renglón de una operación. */
    public String asignarFolio() {
        this.folio = String.format(FORMATO_FOLIO, idPago);
        return folio;
    }

    /** Los demás renglones de una liquidación llevan el folio del primero. */
    public void compartirFolio(String folio) {
        this.folio = folio;
    }

    public BigDecimal totalRecibido() {
        return importe.add(recargo);
    }

    public boolean cuotaLiquidada() {
        return saldoCuota.signum() == 0;
    }

    public boolean vigente() {
        return estatus == EstatusPago.VIGENTE;
    }

    public void cancelar(String usuario, Instant cuando, String motivo) {
        if (!vigente()) {
            throw new IllegalStateException("El pago " + idPago + " ya estaba cancelado");
        }
        this.estatus = EstatusPago.CANCELADO;
        this.canceladoPor = usuario;
        this.fechaCancelacion = cuando;
        this.motivoCancelacion = motivo;
    }
}
