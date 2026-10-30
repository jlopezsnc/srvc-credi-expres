package com.crediexpress.dto.entity;

import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Encabezado del préstamo. Los importes son columnas FLOAT: normalizar con {@code Dinero.de}. */
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

    // Muchos préstamos comparten la misma modalidad.
    @ManyToOne
    @JoinColumn(name = "id_modalidad", referencedColumnName = "id_modalidad")
    private Modalidad idModalidad;

    /** Interés mensual en porcentaje. */
    @Column(name = "tasa_interes")
    private BigDecimal tasaInteres;

    @Column(name = "numero_cuotas")
    private Integer numeroCuotas;

    @Column(name = "importe_cuota")
    private BigDecimal importeCuota;

    @Column(name = "total_pagar")
    private BigDecimal totalPagar;

    /** La pone la base (DEFAULT CURRENT_TIMESTAMP); Hibernate no la envía. */
    @Column(name = "fecha", insertable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "fecha_solicitud")
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_inicio_credito")
    private LocalDate fechaInicioCredito;

    @Column(name = "interes_generado")
    private BigDecimal interesGenerado;

}
