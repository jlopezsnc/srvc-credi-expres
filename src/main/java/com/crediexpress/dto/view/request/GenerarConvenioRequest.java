package com.crediexpress.dto.view.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Genera el convenio. {@code saldoEsperado} es el saldo que el usuario vio en la simulación: si
 * entró un pago mientras capturaba y el saldo cambió, el back responde 409 en lugar de convenir otra
 * cantidad.
 */
@MensajeValidacion(SimularConvenioRequest.MENSAJE)
public record GenerarConvenioRequest(
        @NotNull(message = "Elige al titular del convenio.")
        Integer idClienteTitular,

        @NotEmpty(message = "Elige al menos un préstamo.")
        @Size(max = PlanConvenio.MAXIMO_PRESTAMOS, message = "Un convenio admite hasta 20 préstamos.")
        List<@NotNull(message = "Préstamo inválido.") Integer> idsPrestamo,

        @NotNull(message = "Elige la periodicidad.")
        Integer idModalidad,

        @NotNull(message = "Captura el número de pagos.")
        @Min(value = PlanConvenio.MINIMO_PAGOS, message = "Entre 1 y 52 pagos.")
        @Max(value = PlanConvenio.MAXIMO_PAGOS, message = "Entre 1 y 52 pagos.")
        Integer numeroPagos,

        @NotNull(message = "Captura el interés mensual (0 si no lleva).")
        @DecimalMin(value = "0", message = "El interés no puede ser negativo.")
        @DecimalMax(value = "100", message = "El interés no puede ser mayor a 100%.")
        @Digits(integer = 3, fraction = 2, message = "El interés admite hasta dos decimales.")
        BigDecimal tasaInteres,

        @NotNull(message = "Elige la fecha del primer pago.")
        LocalDate fechaPrimerPago,

        @NotBlank(message = "Escribe el motivo del convenio.")
        @Size(min = 10, max = 250, message = "El motivo debe tener entre 10 y 250 caracteres.")
        String motivo,

        @NotNull(message = "Falta el saldo de la simulación. Actualiza la pantalla.")
        @Digits(integer = 10, fraction = 2, message = "Saldo inválido.")
        BigDecimal saldoEsperado) implements PlanConvenio {
}
