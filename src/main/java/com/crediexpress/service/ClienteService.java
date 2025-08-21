package com.crediexpress.service;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;

import java.util.List;

public interface ClienteService {

    List<ClienteResponse> listarCliente();

    ClienteResponse guardarCliente(ClienteRequest clienteRequest);

    boolean validarRequest(ClienteRequest clienteRequest);
}
