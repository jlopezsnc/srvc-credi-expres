package com.crediexpress.dto.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenio (tabla {@code convenio}, V4): varios préstamos con saldo juntos en un préstamo nuevo a
 * nombre del titular. No tiene setters: se crea con el builder y después solo cambia por
 * {@link #asignarFolio()}, {@link #agregar} y {@link #cancelar}. Nunca se borra.
 */
@Entity
@Table(name = "convenio")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Convenio {

    private static final String FORMATO_FOLIO = "CV-%06d";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_convenio")
    private Integer idConvenio;

    @Column(name = "folio")
    private String folio;

    @Column(name = "llave_idempotencia", nullable = false, updatable = false)
    private String llaveIdempotencia;

    @Column(name = "hash_peticion", nullable = false, updatable = false)
    private String hashPeticion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente_titular", nullable = false, updatable = false)
    private Cliente titular;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prestamo_convenio", nullable = false, updatable = false)
    private CalculadoraPrestamos prestamoConvenio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_modalidad", nullable = false, updatable = false)
    private Modalidad modalidad;

    @Column(name = "numero_pagos", nullable = false, updatable = false)
    private int numeroPagos;

    @Column(name = "fecha_primer_pago", nullable = false, updatable = false)
    private LocalDate fechaPrimerPago;

    @Column(name = "saldo_convenido", nullable = false, updatable = false)
    private BigDecimal saldoConvenido;

    @Column(name = "tasa_interes", nullable = false, updatable = false)
    private BigDecimal tasaInteres;

    @Column(name = "interes", nullable = false, updatable = false)
    private BigDecimal interes;

    @Column(name = "total_pagar", nullable = false, updatable = false)
    private BigDecimal totalPagar;

    @Column(name = "importe_cuota", nullable = false, updatable = false)
    private BigDecimal importeCuota;

    @Column(name = "motivo", nullable = false, updatable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    @Builder.Default
    private EstatusConvenio estatus = EstatusConvenio.VIGENTE;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private String registradoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    @Column(name = "ip_origen", updatable = false)
    private String ipOrigen;

    @Column(name = "actualizado_por")
    private String actualizadoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_actualizacion")
    private Instant fechaActualizacion;

    @Column(name = "cancelado_por")
    private String canceladoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_cancelacion")
    private Instant fechaCancelacion;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(mappedBy = "convenio", cascade = CascadeType.PERSIST)
    @OrderBy("idConvenioPrestamo")
    @Builder.Default
    private List<ConvenioPrestamo> prestamos = new ArrayList<>();

    /** Folio a partir del id generado. */
    public String asignarFolio() {
        this.folio = String.format(FORMATO_FOLIO, idConvenio);
        return folio;
    }

    public boolean vigente() {
        return estatus == EstatusConvenio.VIGENTE;
    }

    /** Lo cancela con su bitácora; los préstamos de origen siguen al convenio. */
    public void cancelar(String usuario, Instant cuando, String motivo) {
        if (!vigente()) {
            throw new IllegalStateException("El convenio " + folio + " ya estaba cancelado");
        }
        this.estatus = EstatusConvenio.CANCELADO;
        this.canceladoPor = usuario;
        this.fechaCancelacion = cuando;
        this.motivoCancelacion = motivo;
        this.actualizadoPor = usuario;
        this.fechaActualizacion = cuando;
        prestamos.forEach(ConvenioPrestamo::cancelar);
    }

    /** Liga un préstamo de origen; se guarda junto con el convenio. */
    public void agregar(ConvenioPrestamo prestamo) {
        prestamo.ligarA(this);
        prestamos.add(prestamo);
    }
}
