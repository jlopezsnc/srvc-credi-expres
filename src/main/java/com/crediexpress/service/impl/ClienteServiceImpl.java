package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    @Override
    @Transactional
    public ClienteResponse actualizarCliente(final Integer idCliente, final ActualizarClienteRequest request) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> ApiException.noEncontrado("No existe el cliente."));
        String curp = request.curp().strip().toUpperCase();
        if (clienteRepository.existsByCurpAndIdClienteNot(curp, idCliente)) {
            throw ApiException.conflicto("Ya existe otro cliente registrado con esta CURP.");
        }
        cliente.setNombreCompleto(request.nombreCompleto().strip());
        cliente.setCurp(curp);
        cliente.setTelefono(opcional(request.telefono()));
        cliente.setCorreoElectronico(opcional(request.correoElectronico()));
        cliente.setDomicilioCalle(opcional(request.domicilioCalle()));
        cliente.setDomicilioColonia(opcional(request.domicilioColonia()));
        cliente.setDomicilioCiudad(opcional(request.domicilioCiudad()));
        return dtoToVo(clienteRepository.saveAndFlush(cliente));
    }

    /** El front manda "" en los campos opcionales vacíos; en la base van como NULL. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
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