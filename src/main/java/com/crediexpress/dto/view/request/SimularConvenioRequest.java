package com.crediexpress.dto.view.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo capturado en "Nuevo convenio" para ver el plan. El back calcula el saldo de los préstamos, el
 * interés, el total y el calendario; el front no calcula nada.
 */
@MensajeValidacion(SimularConvenioRequest.MENSAJE)
public record SimularConvenioRequest(
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
        LocalDate fechaPrimerPago) implements PlanConvenio {

    public static final String MENSAJE = "Revisa los datos del convenio.";
}
