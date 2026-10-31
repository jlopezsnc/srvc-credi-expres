package com.crediexpress.controller;

import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.dto.view.response.PaginaPrestamosResponse;
import com.crediexpress.service.PrestamoConsultaService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista de préstamos con Estado, Próximo pago y Pagos calculados en el back, y el calendario de cada
 * uno. Contrato: docs/API-PRESTAMOS.md del front. (El alta sigue en {@code EstadoPrestamosController}.)
 */
@RestController
@RequestMapping(path = "/operaciones-prestamos", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PrestamoConsultaController {

    private static final String MENSAJE_TAMANO = "El tamaño de página debe estar entre 1 y 100.";

    private final PrestamoConsultaService consultaService;

    @GetMapping("/prestamos")
    public ResponseEntity<GenericResponse> listar(
            @RequestParam(defaultValue = "ACTIVOS") FiltroPrestamos filtro,
            @RequestParam(required = false) @Size(max = 100, message = "La búsqueda admite hasta 100 caracteres.")
            String busqueda,
            @RequestParam(defaultValue = "PROXIMO_PAGO") OrdenPrestamos orden,
            @RequestParam(defaultValue = "ASC") Direccion direccion,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa.") int pagina,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = MENSAJE_TAMANO)
            @Max(value = 100, message = MENSAJE_TAMANO) int tamano) {
        PaginaPrestamosResponse respuesta = consultaService.listar(filtro, busqueda, orden, direccion, pagina, tamano);
        return ok(respuesta, respuesta.totalElementos() + " registros encontrados.");
    }

    @GetMapping("/prestamos/{idPrestamo}")
    public ResponseEntity<GenericResponse> detalle(@PathVariable Integer idPrestamo) {
        return ok(consultaService.detalle(idPrestamo), "Calendario del préstamo.");
    }

    private static ResponseEntity<GenericResponse> ok(Object cuerpo, String mensaje) {
        return ResponseEntity.ok(GenericResponse.builder()
                .objetoResponse(cuerpo)
                .httpStatus(HttpStatus.OK)
                .mensaje(mensaje)
                .build());
    }
}
