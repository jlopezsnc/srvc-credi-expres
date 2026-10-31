package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.SituacionPrestamo;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Plan de un convenio calculado en el back: los préstamos que se convienen con su saldo actual y el
 * calendario nuevo. Es lo que muestra "Nuevo convenio" y lo que imprime la hoja para el cliente.
 */
public record SimulacionConvenioResponse(
        List<PrestamoConvenido> prestamos,
        int personas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoConvenido,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tasaInteres,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal meses,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interes,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPagar,
        int numeroPagos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeCuota,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeUltimaCuota,
        ModalidadResumenResponse modalidad,
        List<CuotaPlan> calendario) {

    /** Un préstamo de origen con cómo está hoy. */
    public record PrestamoConvenido(
            Integer idPrestamo,
            ClienteResumenResponse cliente,
            SituacionPrestamo estado,
            int numeroCuotas,
            int cuotasPagadas,
            int cuotasVencidas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldo) {
    }

    public record CuotaPlan(
            int numero,
            LocalDate fechaVencimiento,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe) {
    }
}
