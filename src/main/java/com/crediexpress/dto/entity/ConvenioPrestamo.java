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

/** Un préstamo de origen de un convenio, con cómo estaba al convenir (tabla {@code convenio_prestamo}, V4). */
@Entity
@Table(name = "convenio_prestamo")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConvenioPrestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_convenio_prestamo")
    private Integer idConvenioPrestamo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_convenio", nullable = false, updatable = false)
    private Convenio convenio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prestamo", nullable = false, updatable = false)
    private CalculadoraPrestamos prestamo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false, updatable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacion_anterior", nullable = false, updatable = false)
    private SituacionPrestamo situacionAnterior;

    @Column(name = "cuotas_convenidas", nullable = false, updatable = false)
    private int cuotasConvenidas;

    @Column(name = "cuotas_vencidas", nullable = false, updatable = false)
    private int cuotasVencidas;

    @Column(name = "abonado_convenido", nullable = false, updatable = false)
    private BigDecimal abonadoConvenido;

    @Column(name = "saldo_convenido", nullable = false, updatable = false)
    private BigDecimal saldoConvenido;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    @Builder.Default
    private EstatusConvenio estatus = EstatusConvenio.VIGENTE;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private String registradoPor;

    @Convert(converter = InstanteUtcConverter.class)
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    void ligarA(Convenio convenio) {
        this.convenio = convenio;
    }

    void cancelar() {
        this.estatus = EstatusConvenio.CANCELADO;
    }
}
