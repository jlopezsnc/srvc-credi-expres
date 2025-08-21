package com.crediexpress.dto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

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

    @Column(name = "numero_cuota")
    private BigDecimal numeroCuota;

    @Column(name = "fecha_vencimiento")
    private Date fechaVencimiento;

    @Column(name = "importe_cuota")
    private BigDecimal importeCuota;

    @Column(name = "fecha_pago")
    private Date fechaPago;

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
