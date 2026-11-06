package com.crediexpress.service;

import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;

import java.util.List;

public interface ClienteService {

    List<ClienteResponse> listarCliente();

    /** @param usuario quién lo da de alta (correo o uid del token), para auditoría. */
    ClienteResponse guardarCliente(ClienteRequest clienteRequest, String usuario);

    boolean validarRequest(ClienteRequest clienteRequest);

    /**
     * Reemplaza los datos capturables del cliente (no cambia su estatus).
     * 404 si no existe; 409 si la CURP pertenece a otro cliente. Guarda quién y cuándo lo editó.
     */
    ClienteResponse actualizarCliente(Integer idCliente, ActualizarClienteRequest request, String usuario);

    /**
     * Pone al cliente INACTIVO. 404 si no existe; 409 si tiene cuotas con saldo (préstamos activos).
     * Si ya estaba inactivo no cambia nada.
     */
    ClienteResponse darDeBaja(Integer idCliente, String usuario);

    /** Pone al cliente ACTIVO. 404 si no existe; si ya estaba activo no cambia nada. */
    ClienteResponse reactivar(Integer idCliente, String usuario);
}
