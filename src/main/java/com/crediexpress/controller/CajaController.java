package com.crediexpress.controller;

import com.crediexpress.config.security.UsuarioAutenticado;
import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.RegistrarMovimientoCajaRequest;
import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.dto.view.response.PaginaResponse;
import com.crediexpress.service.CajaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Saldo de caja (pestaña de Reportes). Contrato: docs/API-CAJA.md del front. */
@RestController
@RequestMapping(path = "/operaciones-caja", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CajaController {

    private static final String MENSAJE_TAMANO = "El tamaño de página debe estar entre 1 y 100.";
    private static final String MENSAJE_PAGINA = "La página no puede ser negativa.";

    private final CajaService cajaService;

    /** {@code anio} vacío = toda la historia. */
    @GetMapping("/saldo")
    public ResponseEntity<GenericResponse> saldo(@RequestParam(required = false) Integer anio) {
        return respuesta(HttpStatus.OK, cajaService.saldo(anio), "Saldo de caja.");
    }

    @GetMapping("/movimientos")
    public ResponseEntity<GenericResponse> movimientos(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = MENSAJE_PAGINA) int pagina,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = MENSAJE_TAMANO)
            @Max(value = 100, message = MENSAJE_TAMANO) int tamano) {
        PaginaResponse<?> lista = cajaService.movimientos(pagina, tamano);
        return respuesta(HttpStatus.OK, lista, lista.totalElementos() + " registros encontrados.");
    }

    @PostMapping(path = "/movimiento-registrar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> registrar(
            @Valid @RequestBody RegistrarMovimientoCajaRequest request,
            @AuthenticationPrincipal Jwt token) {
        return respuesta(HttpStatus.CREATED, cajaService.registrar(request, UsuarioAutenticado.de(token)),
                "Movimiento registrado.");
    }

    @PutMapping(path = "/movimiento-cancelar/{idMovimiento}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> cancelar(
            @PathVariable Integer idMovimiento,
            @Valid @RequestBody CancelarPagoRequest request,
            @AuthenticationPrincipal Jwt token) {
        return respuesta(HttpStatus.OK, cajaService.cancelar(idMovimiento, request, UsuarioAutenticado.de(token)),
                "Movimiento cancelado.");
    }

    private static ResponseEntity<GenericResponse> respuesta(HttpStatus status, Object cuerpo, String mensaje) {
        return ResponseEntity.status(status).body(GenericResponse.builder()
                .objetoResponse(cuerpo)
                .httpStatus(status)
                .mensaje(mensaje)
                .build());
    }
}
