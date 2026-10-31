package com.crediexpress.service.cobranza;

import com.crediexpress.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Encabezado {@code Idempotency-Key}: un UUID que el front genera al abrir el diálogo y repite si
 * reintenta. Con él, un doble clic o un reintento por red no cobran dos veces.
 */
public record LlaveIdempotencia(String valor) {

    public static final String ENCABEZADO = "Idempotency-Key";
    static final String MENSAJE_INVALIDA =
            "Falta el identificador de la operación. Recarga la página e intenta de nuevo.";

    public static LlaveIdempotencia de(String encabezado) {
        if (encabezado == null || encabezado.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MENSAJE_INVALIDA);
        }
        try {
            // Normaliza mayúsculas y descarta formatos abreviados que UUID.fromString acepta.
            UUID uuid = UUID.fromString(encabezado.strip());
            String normalizada = uuid.toString();
            if (!normalizada.equalsIgnoreCase(encabezado.strip())) {
                throw new IllegalArgumentException("UUID no canónico");
            }
            return new LlaveIdempotencia(normalizada);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MENSAJE_INVALIDA);
        }
    }
}
