package com.crediexpress.exception;

import com.crediexpress.dto.view.response.CampoError;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Error de negocio que llega al usuario: el código HTTP y un mensaje en español, sin datos internos.
 * Lo convierte en el sobre de respuesta {@link com.crediexpress.controller.ApiExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final transient List<CampoError> campos;

    public ApiException(HttpStatus status, String mensaje) {
        this(status, mensaje, null);
    }

    public ApiException(HttpStatus status, String mensaje, List<CampoError> campos) {
        super(mensaje);
        this.status = status;
        this.campos = campos;
    }

    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, mensaje);
    }

    public static ApiException conflicto(String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, mensaje);
    }

    public static ApiException datosInvalidos(String mensaje, List<CampoError> campos) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensaje, campos);
    }
}
