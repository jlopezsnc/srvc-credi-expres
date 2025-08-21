package com.crediexpress.dto.entity;

import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.CascadeType;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

@Entity
@Table(name = "calculadora_prestamos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CalculadoraPrestamos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calculadora_prestamo")
    private Integer idCalculadoraPrestamo;

    @ManyToOne
    @JoinColumn(name = "id_cliente", referencedColumnName = "id_cliente")
    private Cliente idCliente;

    @Column(name = "importe_credito")
    private BigDecimal importeCredito;

    @OneToOne
    @JoinColumn(name = "id_modalidad", referencedColumnName = "id_modalidad")
    private Modalidad idModalidad;

    @Column(name = "tasa_interes")
    private BigDecimal tasaInteres;

    @Column(name = "numero_cuotas")
    private Integer numeroCuotas;

    @Column(name = "importe_cuota")
    private BigDecimal importeCuota;

    @Column(name = "total_pagar")
    private BigDecimal totalPagar;

    @Column(name = "fecha")
    private Date fecha;

    @Column(name = "fecha_solicitud")
    private Date fechaSolicitud;

    @Column(name = "fecha_inicio_credito")
    private Date fechaInicioCredito;

    @Column(name = "interes_generado")
    private BigDecimal interesGenerado;

}