package com.crediexpress.dto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

}
