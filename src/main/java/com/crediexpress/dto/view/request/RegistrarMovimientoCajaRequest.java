package com.crediexpress.dto.view.request;

import com.crediexpress.dto.entity.TipoMovimientoCaja;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Aportación de capital o retiro. Que la fecha no sea futura se valida en el servicio (con el reloj). */
@MensajeValidacion(RegistrarMovimientoCajaRequest.MENSAJE)
public record RegistrarMovimientoCajaRequest(
        @NotNull(message = "Elige si es aportación o retiro.")
        TipoMovimientoCaja tipo,

        @NotNull(message = "Captura el importe.")
        @DecimalMin(value = "0.01", message = "El importe debe ser mayor a cero.")
        @Digits(integer = 10, fraction = 2, message = "El importe admite hasta dos decimales.")
        BigDecimal importe,

        @NotNull(message = "Elige la fecha.")
        LocalDate fecha,

        @NotBlank(message = RegistrarMovimientoCajaRequest.MENSAJE_CONCEPTO)
        @Size(min = 3, max = 200, message = RegistrarMovimientoCajaRequest.MENSAJE_CONCEPTO)
        String concepto) {

    public static final String MENSAJE = "Revisa los datos del movimiento.";
    static final String MENSAJE_CONCEPTO = "Escribe el concepto (de 3 a 200 caracteres).";
}
