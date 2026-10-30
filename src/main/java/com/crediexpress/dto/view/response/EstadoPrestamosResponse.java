package com.crediexpress.dto.view.response;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class EstadoPrestamosResponse {
    private Integer id;
    private ClienteResponse clienteResponse;
    private ModalidadResponse modalidadResponse;
    private Integer numeroCuota;
    private LocalDate fechaVencimiento;
    private BigDecimal importeCuota;
    private LocalDate fechaPago;
    private Integer estadoPago;
    private Integer diasMoratorios;
    private String alerta;
    private Integer idCalculadoraPrestamo;
}
