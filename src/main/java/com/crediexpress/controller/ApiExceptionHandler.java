package com.crediexpress.controller;

import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.exception.ApiException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Todas las respuestas de error salen con el mismo sobre que las de éxito
 * ({@code objetoResponse}, {@code httpStatus}, {@code mensaje} y, en validaciones, {@code campos}),
 * con el código HTTP real y sin trazas ni nombres de clases.
 * Los 401 sin token los responde Spring Security antes de llegar aquí.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String MENSAJE_VALIDACION = "Revisa los datos enviados.";
    static final String MENSAJE_CUERPO_INVALIDO = "El cuerpo de la petición no es válido.";
    static final String MENSAJE_PARAMETROS = "Revisa los parámetros de la petición.";
    static final String MENSAJE_INESPERADO = "Ocurrió un error inesperado. Intenta de nuevo.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<GenericResponse> negocio(ApiException e) {
        return respuesta(e.getStatus(), e.getMessage(), e.getCampos());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> cuerpoNoValido(MethodArgumentNotValidException e) {
        List<CampoError> campos = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new CampoError(error.getField(), error.getDefaultMessage()))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, campos);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<GenericResponse> parametrosNoValidos(HandlerMethodValidationException e) {
        List<CampoError> campos = e.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> new CampoError(
                                resultado.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, MENSAJE_PARAMETROS, campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse> restriccionNoValida(ConstraintViolationException e) {
        List<CampoError> campos = e.getConstraintViolations().stream()
                .map(violacion -> new CampoError(ultimoNodo(violacion.getPropertyPath().toString()),
                        violacion.getMessage()))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> cuerpoIlegible(HttpMessageNotReadableException e) {
        return respuesta(HttpStatus.BAD_REQUEST, MENSAJE_CUERPO_INVALIDO, null);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<GenericResponse> parametroInvalido(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, MENSAJE_PARAMETROS, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<GenericResponse> noExiste(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "El recurso no existe.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<GenericResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<GenericResponse> tipoNoSoportado(HttpMediaTypeNotSupportedException e) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envía el cuerpo como JSON.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> inesperado(Exception e) {
        // La traza solo va al log; al usuario nunca se le muestran detalles internos.
        log.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, MENSAJE_INESPERADO, null);
    }

    private static ResponseEntity<GenericResponse> respuesta(HttpStatus status, String mensaje,
                                                             List<CampoError> campos) {
        return ResponseEntity.status(status).body(GenericResponse.builder()
                .httpStatus(status)
                .mensaje(mensaje)
                .campos(campos == null || campos.isEmpty() ? null : campos)
                .build());
    }

    /** "registrar.request.importe" → "importe". */
    private static String ultimoNodo(String ruta) {
        int punto = ruta.lastIndexOf('.');
        return punto < 0 ? ruta : ruta.substring(punto + 1);
    }
}
