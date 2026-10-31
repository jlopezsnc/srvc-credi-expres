package com.crediexpress.controller;

import com.crediexpress.config.security.UsuarioAutenticado;
import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.SituacionCuota;
import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.LiquidarPrestamoRequest;
import com.crediexpress.dto.view.request.OrdenCuotas;
import com.crediexpress.dto.view.request.RegistrarPagoRequest;
import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.dto.view.response.PaginaConTotalResponse;
import com.crediexpress.dto.view.response.PaginaResponse;
import com.crediexpress.service.CobranzaConsultaService;
import com.crediexpress.service.PagoService;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.ResultadoOperacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Pantalla de cobranza. Contrato: docs/API-COBRANZA.md del front. */
@RestController
@RequestMapping(path = "/operaciones-cobranza", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CobranzaController {

    private static final String MENSAJE_TAMANO = "El tamaño de página debe estar entre 1 y 100.";
    private static final String MENSAJE_PAGINA = "La página no puede ser negativa.";
    private static final String MENSAJE_BUSQUEDA = "La búsqueda admite hasta 100 caracteres.";
    private static final String MENSAJE_DIAS = "Los días deben estar entre 1 y 31.";

    private final CobranzaConsultaService consultaService;
    private final PagoService pagoService;

    @GetMapping("/resumen")
    public ResponseEntity<GenericResponse> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(defaultValue = "7") @Min(value = 1, message = MENSAJE_DIAS)
            @Max(value = 31, message = MENSAJE_DIAS) int dias) {
        return ok(consultaService.resumen(fecha, dias), "Resumen de cobranza.");
    }

    @GetMapping("/cuotas-pendientes")
    public ResponseEntity<GenericResponse> cuotasPendientes(
            @RequestParam(defaultValue = "HOY") SituacionCuota situacion,
            @RequestParam(defaultValue = "7") @Min(value = 1, message = MENSAJE_DIAS)
            @Max(value = 31, message = MENSAJE_DIAS) int dias,
            @RequestParam(required = false) @Size(max = 100, message = MENSAJE_BUSQUEDA) String busqueda,
            @RequestParam(defaultValue = "VENCE") OrdenCuotas orden,
            @RequestParam(defaultValue = "ASC") Direccion direccion,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = MENSAJE_PAGINA) int pagina,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = MENSAJE_TAMANO)
            @Max(value = 100, message = MENSAJE_TAMANO) int tamano) {
        PaginaConTotalResponse<?> respuesta =
                consultaService.cuotasPendientes(situacion, dias, busqueda, orden, direccion, pagina, tamano);
        return ok(respuesta, respuesta.totalElementos() + " registros encontrados.");
    }

    @PostMapping(path = "/pago-registrar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> registrarPago(
            @RequestHeader(name = LlaveIdempotencia.ENCABEZADO, required = false) String llave,
            @Valid @RequestBody RegistrarPagoRequest request,
            @AuthenticationPrincipal Jwt token) {
        ResultadoOperacion<?> resultado = pagoService.registrar(request, LlaveIdempotencia.de(llave),
                UsuarioAutenticado.de(token));
        return creadoORepetido(resultado, "Pago registrado.");
    }

    @GetMapping("/pagos")
    public ResponseEntity<GenericResponse> pagos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) EstatusPago estatus,
            @RequestParam(required = false) @Size(max = 100, message = MENSAJE_BUSQUEDA) String busqueda,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = MENSAJE_PAGINA) int pagina,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = MENSAJE_TAMANO)
            @Max(value = 100, message = MENSAJE_TAMANO) int tamano) {
        return lista(consultaService.pagos(fecha, estatus, busqueda, pagina, tamano));
    }

    /** "Cobrado hoy": un renglón por operación (folio), solo vigentes, con el total que entró en caja. */
    @GetMapping("/operaciones")
    public ResponseEntity<GenericResponse> operaciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @Size(max = 100, message = MENSAJE_BUSQUEDA) String busqueda,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = MENSAJE_PAGINA) int pagina,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = MENSAJE_TAMANO)
            @Max(value = 100, message = MENSAJE_TAMANO) int tamano) {
        PaginaConTotalResponse<?> respuesta = consultaService.operaciones(fecha, busqueda, pagina, tamano);
        return ok(respuesta, respuesta.totalElementos() + " registros encontrados.");
    }

    @PutMapping(path = "/pago-cancelar/{idPago}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> cancelarPago(
            @PathVariable Integer idPago,
            @Valid @RequestBody CancelarPagoRequest request,
            @AuthenticationPrincipal Jwt token) {
        return ok(pagoService.cancelar(idPago, request, UsuarioAutenticado.de(token)), "Pago cancelado.");
    }

    /** Reimprimir el recibo de una operación ya cobrada. */
    @GetMapping("/recibo/{folio}")
    public ResponseEntity<GenericResponse> recibo(
            @PathVariable @Size(max = 12, message = "El folio no es válido.") String folio) {
        return ok(consultaService.recibo(folio), "Recibo del pago.");
    }

    @GetMapping("/prestamo-saldo/{idPrestamo}")
    public ResponseEntity<GenericResponse> saldoPrestamo(@PathVariable Integer idPrestamo) {
        return ok(consultaService.saldoPrestamo(idPrestamo), "Saldo del préstamo.");
    }

    @PostMapping(path = "/prestamo-liquidar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> liquidarPrestamo(
            @RequestHeader(name = LlaveIdempotencia.ENCABEZADO, required = false) String llave,
            @Valid @RequestBody LiquidarPrestamoRequest request,
            @AuthenticationPrincipal Jwt token) {
        ResultadoOperacion<?> resultado = pagoService.liquidar(request, LlaveIdempotencia.de(llave),
                UsuarioAutenticado.de(token));
        return creadoORepetido(resultado, "Préstamo liquidado.");
    }

    private static ResponseEntity<GenericResponse> lista(PaginaResponse<?> pagina) {
        return ok(pagina, pagina.totalElementos() + " registros encontrados.");
    }

    /** 201 la primera vez; 200 con lo mismo si se repite la petición con la misma llave. */
    private static ResponseEntity<GenericResponse> creadoORepetido(ResultadoOperacion<?> resultado, String mensaje) {
        return resultado.repetida()
                ? respuesta(HttpStatus.OK, resultado.valor(), "Este pago ya estaba registrado.")
                : respuesta(HttpStatus.CREATED, resultado.valor(), mensaje);
    }

    private static ResponseEntity<GenericResponse> ok(Object cuerpo, String mensaje) {
        return respuesta(HttpStatus.OK, cuerpo, mensaje);
    }

    private static ResponseEntity<GenericResponse> respuesta(HttpStatus status, Object cuerpo, String mensaje) {
        return ResponseEntity.status(status).body(GenericResponse.builder()
                .objetoResponse(cuerpo)
                .httpStatus(status)
                .mensaje(mensaje)
                .build());
    }
}
