package com.crediexpress.dto.view.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de la cancelación de un convenio; queda en su bitácora. */
@MensajeValidacion("Revisa el motivo.")
public record CancelarConvenioRequest(
        @NotBlank(message = CancelarConvenioRequest.MENSAJE_MOTIVO)
        @Size(min = 10, max = 250, message = CancelarConvenioRequest.MENSAJE_MOTIVO)
        String motivo) {

    static final String MENSAJE_MOTIVO = "Escribe el motivo de la cancelación (10 a 250 caracteres).";
}
