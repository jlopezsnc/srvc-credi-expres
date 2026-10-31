package com.crediexpress.dto.view.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@MensajeValidacion("Revisa el motivo.")
public record CancelarPagoRequest(
        @NotBlank(message = CancelarPagoRequest.MENSAJE_MOTIVO)
        @Size(min = 5, max = 200, message = CancelarPagoRequest.MENSAJE_MOTIVO)
        String motivo) {

    static final String MENSAJE_MOTIVO = "Escribe el motivo de la cancelación (mínimo 5 caracteres).";
}
