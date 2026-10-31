package com.crediexpress.controller;

import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Pantalla de reportes. Contrato: docs/API-REPORTES.md del front. */
@RestController
@RequestMapping(path = "/operaciones-reportes", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/cobranza")
    public ResponseEntity<GenericResponse> cobranza(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ok(reporteService.cobranza(desde, hasta), "Reporte de cobranza.");
    }

    @GetMapping("/cartera")
    public ResponseEntity<GenericResponse> cartera() {
        return ok(reporteService.cartera(), "Reporte de cartera.");
    }

    private static ResponseEntity<GenericResponse> ok(Object cuerpo, String mensaje) {
        return ResponseEntity.ok(GenericResponse.builder()
                .objetoResponse(cuerpo)
                .httpStatus(HttpStatus.OK)
                .mensaje(mensaje)
                .build());
    }
}
