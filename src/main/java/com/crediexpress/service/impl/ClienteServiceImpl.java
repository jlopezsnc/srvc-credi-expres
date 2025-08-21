package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClienteServiceImpl implements ClienteService {


    @Autowired
    private ClienteRepository clienteRepository;

    @Override
    public List<ClienteResponse> listarCliente() {
        List<ClienteResponse> clienteResponses = new ArrayList<>();
        clienteRepository.findAll().forEach(e -> {
            clienteResponses.add(dtoToVo(e));
        });
        return clienteResponses;
    }


    @Override
    public ClienteResponse guardarCliente(final ClienteRequest clienteRequest) {
        return dtoToVo(clienteRepository.saveAndFlush(voToDto(clienteRequest)));
    }


    private Cliente voToDto(ClienteRequest clienteRequest) {
        return Cliente.builder()
                .curp(clienteRequest.getCurp())
                .nombreCompleto(clienteRequest.getNombreCompleto())
                .telefono(clienteRequest.getTelefono())
                .domicilioCiudad(clienteRequest.getDomicilioCiudad())
                .domicilioCalle(clienteRequest.getDomicilioCalle())
                .domicilioColonia(clienteRequest.getDomicilioColonia())
                .correoElectronico(clienteRequest.getCorreoElectronico())
                .fechaAlta(new Date(new java.util.Date().getTime()))
                .build();
    }

    private ClienteResponse dtoToVo(final Cliente cliente) {
        return ClienteResponse.builder()
                .domicilioCalle(cliente.getDomicilioCalle())
                .idCliente(cliente.getIdCliente())
                .curp(cliente.getCurp())
                .telefono(cliente.getTelefono())
                .domicilioCiudad(cliente.getDomicilioCiudad())
                .correoElectronico(cliente.getCorreoElectronico())
                .domicilioColonia(cliente.getDomicilioColonia())
                .nombreCompleto(cliente.getNombreCompleto())
                .build();
    }

    @Override
    public boolean validarRequest(ClienteRequest clienteRequest) {
        int errores = 0;
        if (clienteRequest.getCurp().isEmpty()) {
            errores += 1;
        }
        if (clienteRequest.getDomicilioCalle().isEmpty()) {
            errores += 1;
        }
        if (clienteRequest.getDomicilioColonia().isEmpty()) {
            errores += 1;
        }
        if (clienteRequest.getDomicilioCiudad().isEmpty()) {
            errores += 1;
        }
        if (clienteRequest.getNombreCompleto().isEmpty()) {
            errores += 1;
        }
        return errores != 0;
    }
}