package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstatusCliente;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClienteServiceImpl implements ClienteService {


    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private EstadoPrestamosRepository estadoPrestamosRepository;

    @Autowired
    private Clock clock;

    @Override
    public List<ClienteResponse> listarCliente() {
        List<ClienteResponse> clienteResponses = new ArrayList<>();
        clienteRepository.findAll().forEach(e -> {
            clienteResponses.add(dtoToVo(e));
        });
        return clienteResponses;
    }


    @Override
    public ClienteResponse guardarCliente(final ClienteRequest clienteRequest, final String usuario) {
        Cliente cliente = voToDto(clienteRequest);
        cliente.setRegistradoPor(usuario);
        return dtoToVo(clienteRepository.saveAndFlush(cliente));
    }


    @Override
    @Transactional
    public ClienteResponse actualizarCliente(final Integer idCliente, final ActualizarClienteRequest request,
                                             final String usuario) {
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
        cliente.setActualizadoPor(usuario);
        cliente.setFechaActualizacion(Instant.now(clock));
        return dtoToVo(clienteRepository.saveAndFlush(cliente));
    }

    @Override
    @Transactional
    public ClienteResponse darDeBaja(final Integer idCliente, final String usuario) {
        Cliente cliente = buscar(idCliente);
        if (cliente.getEstatusCliente() == EstatusCliente.INACTIVO) {
            return dtoToVo(cliente);
        }
        if (estadoPrestamosRepository.existsConSaldoDelCliente(idCliente, EstadoCuota.codigosConSaldo())) {
            throw ApiException.conflicto(
                    "El cliente tiene préstamos activos. Liquídalos o cancélalos antes de darlo de baja.");
        }
        return cambiarEstatus(cliente, EstatusCliente.INACTIVO, usuario);
    }

    @Override
    @Transactional
    public ClienteResponse reactivar(final Integer idCliente, final String usuario) {
        Cliente cliente = buscar(idCliente);
        if (cliente.getEstatusCliente() == EstatusCliente.ACTIVO) {
            return dtoToVo(cliente);
        }
        return cambiarEstatus(cliente, EstatusCliente.ACTIVO, usuario);
    }

    private Cliente buscar(final Integer idCliente) {
        return clienteRepository.findById(idCliente)
                .orElseThrow(() -> ApiException.noEncontrado("No existe el cliente."));
    }

    private ClienteResponse cambiarEstatus(final Cliente cliente, final EstatusCliente estatus, final String usuario) {
        cliente.setEstatusCliente(estatus);
        cliente.setActualizadoPor(usuario);
        cliente.setFechaActualizacion(Instant.now(clock));
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
                .estatusCliente(EstatusCliente.ACTIVO)
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
                .estatusCliente(cliente.getEstatusCliente() == null ? null : cliente.getEstatusCliente().name())
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