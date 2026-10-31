package com.crediexpress.service;

import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;

import java.util.List;

public interface ClienteService {

    List<ClienteResponse> listarCliente();

    ClienteResponse guardarCliente(ClienteRequest clienteRequest);

    boolean validarRequest(ClienteRequest clienteRequest);

    /**
     * Reemplaza los datos capturables del cliente (no cambia su estatus).
     * 404 si no existe; 409 si la CURP pertenece a otro cliente.
     */
    ClienteResponse actualizarCliente(Integer idCliente, ActualizarClienteRequest request);
}
