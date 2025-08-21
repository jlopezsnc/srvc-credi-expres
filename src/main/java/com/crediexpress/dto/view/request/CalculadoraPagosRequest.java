package com.crediexpress.dto.view.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CalculadoraPagosRequest {
    private Integer id;
    private Integer idCliente;
    private Integer idModalidad;
    private BigDecimal numeroCuota;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date fechaVencimiento;
    private BigDecimal importeCuota;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date fechaPago;
    private Integer estadoPago;
    private Integer diasMoratorios;
    private String alerta;
    private Integer idCalculadoraPrestamo;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date fechaGeneracionOperacion; //fecha que sera enviada desde la pantalla de la calculadora
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date fechaInicioCredito; //fecha que va iniciar el credito, dato enviado desde la pantalla
    private Float importeCredito;
    private Float tasaInteres;
    private Float totalPagar;
    private Float interesGenerado;

}
