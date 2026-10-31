package com.crediexpress.controller;

import com.crediexpress.config.security.UsuarioAutenticado;
import com.crediexpress.dto.view.request.CancelarConvenioRequest;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroConvenios;
import com.crediexpress.dto.view.request.GenerarConvenioRequest;
import com.crediexpress.dto.view.request.OrdenConvenios;
import com.crediexpress.dto.view.request.SimularConvenioRequest;
import com.crediexpress.dto.view.response.ConvenioDetalleResponse;
import com.crediexpress.dto.view.response.ConvenioResponse;
import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.dto.view.response.PaginaConveniosResponse;
import com.crediexpress.service.ConvenioConsultaService;
import com.crediexpress.service.ConvenioService;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.ResultadoOperacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Convenios: lista, detalle, simulación, generación y cancelación. La lista de préstamos a convenir
 * es {@code GET /operaciones-prestamos/prestamos} con {@code filtro=ACTIVOS|AL_CORRIENTE|CON_ATRASO}.
 * Contrato: docs/API-CONVENIOS.md del front.
 */
@RestController
@RequestMapping(path = "/operaciones-convenios", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ConvenioController {

    private static final String MENSAJE_TAMANO = "El tamaño de página debe estar entre 1 y 100.";

    private final ConvenioService convenioService;
    private final ConvenioConsultaService consultaService;

    /** Lista de convenios: filtro, búsqueda, orden y páginas en el back. */
    @GetMapping("/convenios")
    public ResponseEntity<GenericResponse> listar(
            @RequestParam(defaultValue = "VIGENTES") FiltroConvenios filtro,
            @RequestParam(required = false) @Size(max = 100, message = "La búsqueda admite hasta 100 caracteres.")
            String busqueda,
            @RequestParam(defaultValue = "FECHA") OrdenConvenios orden,
            @RequestParam(defaultValue = "DESC") Direccion direccion,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa.") int pagina,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = MENSAJE_TAMANO)
            @Max(value = 100, message = MENSAJE_TAMANO) int tamano) {
        PaginaConveniosResponse respuesta = consultaService.listar(filtro, busqueda, orden, direccion, pagina, tamano);
        return respuesta(HttpStatus.OK, respuesta, respuesta.totalElementos() + " registros encontrados.");
    }

    @GetMapping("/convenios/{idConvenio}")
    public ResponseEntity<GenericResponse> detalle(@PathVariable Integer idConvenio) {
        return respuesta(HttpStatus.OK, consultaService.detalle(idConvenio), "Detalle del convenio.");
    }

    /** Solo un convenio vigente y sin pagos; el motivo queda en la bitácora con el usuario del token. */
    @PutMapping(path = "/convenio-cancelar/{idConvenio}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> cancelar(
            @PathVariable Integer idConvenio,
            @Valid @RequestBody CancelarConvenioRequest request,
            @AuthenticationPrincipal Jwt token) {
        ConvenioDetalleResponse detalle = convenioService.cancelar(idConvenio, request, UsuarioAutenticado.de(token));
        return respuesta(HttpStatus.OK, detalle, "Convenio " + detalle.convenio().folio() + " cancelado.");
    }

    /** Saldo, interés, total y calendario del convenio con lo capturado. No guarda nada. */
    @PostMapping(path = "/simular", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> simular(@Valid @RequestBody SimularConvenioRequest request) {
        return respuesta(HttpStatus.OK, convenioService.simular(request), "Simulación del convenio.");
    }

    @PostMapping(path = "/convenio-generar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> generar(
            @RequestHeader(name = LlaveIdempotencia.ENCABEZADO, required = false) String llave,
            @Valid @RequestBody GenerarConvenioRequest request,
            @AuthenticationPrincipal Jwt token,
            HttpServletRequest peticion) {
        ResultadoOperacion<ConvenioResponse> resultado = convenioService.generar(request, LlaveIdempotencia.de(llave),
                UsuarioAutenticado.de(token), peticion.getRemoteAddr());
        return resultado.repetida()
                ? respuesta(HttpStatus.OK, resultado.valor(), "Este convenio ya estaba registrado.")
                : respuesta(HttpStatus.CREATED, resultado.valor(), "Convenio " + resultado.valor().folio() + " generado.");
    }

    private static ResponseEntity<GenericResponse> respuesta(HttpStatus status, Object cuerpo, String mensaje) {
        return ResponseEntity.status(status).body(GenericResponse.builder()
                .objetoResponse(cuerpo)
                .httpStatus(status)
                .mensaje(mensaje)
                .build());
    }
}
