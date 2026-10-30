package com.crediexpress.controller;

import com.crediexpress.dto.view.request.CalculadoraPagosRequest;
import com.crediexpress.dto.view.response.CalcularPagoResponse;
import com.crediexpress.dto.view.response.EstadoPrestamosResponse;
import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.service.EstadoPrestamosService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;


@RestController
@RequestMapping("/operaciones-prestamos")
public class EstadoPrestamosController {

    private EstadoPrestamosService estadoPrestamosService;

    public EstadoPrestamosController(EstadoPrestamosService estadoPrestamosService) {
        this.estadoPrestamosService = estadoPrestamosService;
    }

    @GetMapping(path = "/buscar-ep/{idCliente}", produces = "application/json")
    public ResponseEntity<List<EstadoPrestamosResponse>> buscarEstPrest(@PathVariable(name = "idCliente") Integer id) {
        List<EstadoPrestamosResponse> prestamosResponse = estadoPrestamosService.getEstadoPrestamosByIdCliente(id);

        if (prestamosResponse == null || prestamosResponse.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(prestamosResponse);
    }

    @GetMapping(path = "/buscar-ep/todo", produces = "application/json")
    public ResponseEntity<List<EstadoPrestamosResponse>> buscarTodosEstPrest() {
        List<EstadoPrestamosResponse> estadoPrestamosResponses =
                estadoPrestamosService.getEstadosPrestamos();
        return ResponseEntity.status(HttpStatus.OK).body(estadoPrestamosResponses);
    }

    /** Genera el préstamo y su calendario. El back calcula interés, total y cuotas (no los recibe). */
    @PostMapping(path = "/calcular-pagos", produces = "application/json")
    public ResponseEntity<GenericResponse> guardarEstadoPrestamo(
            @Valid @RequestBody CalculadoraPagosRequest calculadoraPagosRequest) {
        CalcularPagoResponse generado = estadoPrestamosService.guardarEstadoPrestamos(calculadoraPagosRequest);
        return ResponseEntity.ok(
                GenericResponse.builder()
                        .objetoResponse(generado)
                        .httpStatus(HttpStatus.OK)
                        .mensaje(generado.getResultado())
                        .build());
    }

}