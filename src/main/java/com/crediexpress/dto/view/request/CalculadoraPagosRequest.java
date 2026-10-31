package com.crediexpress.dto.view.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que captura la pantalla "Nuevo préstamo". El interés, el total, las cuotas y las fechas los
 * calcula el back ({@link com.crediexpress.service.CalculadoraPrestamo}); si el front manda
 * {@code importeCuota}, {@code totalPagar}, {@code interesGenerado}, {@code estadoPago},
 * {@code alerta} u otros campos, se ignoran.
 */
@MensajeValidacion("Revisa los datos del préstamo.")
public record CalculadoraPagosRequest(
        @NotNull(message = "Selecciona el cliente.")
        Integer idCliente,

        @NotNull(message = "Elige la periodicidad.")
        Integer idModalidad,

        @NotNull(message = "Elige el número de pagos.")
        Integer numeroCuota,

        @NotNull(message = "Captura el monto solicitado.")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero.")
        @Digits(integer = 10, fraction = 2, message = "El monto admite hasta dos decimales.")
        BigDecimal importeCredito,

        /* Interés mensual en porcentaje: 12 = 12% por mes. */
        @NotNull(message = "Captura el interés mensual.")
        @DecimalMin(value = "0", inclusive = false, message = "El interés debe ser mayor a 0%.")
        @DecimalMax(value = "100", message = "El interés no puede ser mayor a 100%.")
        @Digits(integer = 3, fraction = 2, message = "El interés admite hasta dos decimales.")
        BigDecimal tasaInteres,

        @NotNull(message = "Elige la fecha del primer pago.")
        LocalDate fechaInicioCredito) {
}
