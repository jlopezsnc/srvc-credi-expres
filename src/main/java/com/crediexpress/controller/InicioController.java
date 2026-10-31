package com.crediexpress.controller;

import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.service.InicioService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Pantalla de Inicio. Contrato: docs/API-INICIO.md del front. */
@RestController
@RequestMapping(path = "/operaciones-inicio", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InicioController {

    private final InicioService inicioService;

    /** @param mes {@code AAAA-MM}; sin él, el mes actual */
    @GetMapping("/resumen")
    public ResponseEntity<GenericResponse> resumen(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return ResponseEntity.ok(GenericResponse.builder()
                .objetoResponse(inicioService.resumen(mes))
                .httpStatus(HttpStatus.OK)
                .mensaje("Resumen de inicio.")
                .build());
    }
}
