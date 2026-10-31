package com.crediexpress.dto.view.request;

import com.crediexpress.dto.entity.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Pago de todo lo que se debe de un préstamo; {@code importe} debe ser igual al saldo total. */
@MensajeValidacion(LiquidarPrestamoRequest.MENSAJE)
public record LiquidarPrestamoRequest(
        @NotNull(message = "Selecciona el préstamo.")
        Integer idPrestamo,

        @NotNull(message = "Captura el importe.")
        @DecimalMin(value = "0.01", message = "El importe debe ser mayor a cero.")
        @Digits(integer = 10, fraction = 2, message = "El importe admite hasta dos decimales.")
        BigDecimal importe,

        @DecimalMin(value = "0", message = "El recargo no puede ser negativo.")
        @Digits(integer = 10, fraction = 2, message = "El recargo admite hasta dos decimales.")
        BigDecimal recargo,

        @NotNull(message = "Elige la fecha del pago.")
        LocalDate fechaPago,

        @NotNull(message = "Elige el método de pago.")
        MetodoPago metodoPago,

        @Size(max = 50, message = "La referencia admite hasta 50 caracteres.")
        String referencia,

        @Size(max = 200, message = "La nota admite hasta 200 caracteres.")
        String nota) implements SolicitudPago {

    public static final String MENSAJE = "Revisa los datos de la liquidación.";
}
