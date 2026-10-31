package com.crediexpress.controller;

import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.dto.view.response.GenericResponse;
import com.crediexpress.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/operaciones-cliente")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping("/cliente-lst")
    public ResponseEntity<GenericResponse> listaCliente() {
        List<ClienteResponse> clienteResponses = clienteService.listarCliente();
        if (clienteResponses == null || clienteResponses.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.status(HttpStatus.OK).body(
                GenericResponse.builder()
                        .objetoResponse(clienteResponses)
                        .mensaje(clienteResponses.size() + " registros encontrados.")
                        .httpStatus(HttpStatus.OK)
                        .build());
    }

    @PostMapping("/cliente-agregar")
    public ResponseEntity<GenericResponse> agregarCliente(
            @RequestBody(required = true) @NonNull ClienteRequest clienteRequest) {
        if(clienteService.validarRequest(clienteRequest)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GenericResponse.builder()
                            .mensaje("Enviar los campos requeridos.")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                    .build());
        }
        ClienteResponse response = clienteService.guardarCliente(clienteRequest);
        return ResponseEntity.status(HttpStatus.OK).body(GenericResponse.builder()
                        .objetoResponse(response)
                        .httpStatus(HttpStatus.OK)
                .build());
    }

    @PutMapping("/cliente-actualizar/{idCliente}")
    public ResponseEntity<GenericResponse> actualizarCliente(
            @PathVariable Integer idCliente, @Valid @RequestBody ActualizarClienteRequest request) {
        ClienteResponse response = clienteService.actualizarCliente(idCliente, request);
        return ResponseEntity.ok(GenericResponse.builder()
                .objetoResponse(response)
                .mensaje("Cliente actualizado.")
                .httpStatus(HttpStatus.OK)
                .build());
    }
}