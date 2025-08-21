package com.crediexpress.service;
import com.crediexpress.dto.view.request.CalculadoraPagosRequest;
import com.crediexpress.dto.view.response.EstadoPrestamosResponse;

import java.util.List;


public interface EstadoPrestamosService {

    List<EstadoPrestamosResponse> getEstadosPrestamos();

    EstadoPrestamosResponse getEstadoPrestamosById(Integer id);

    List<EstadoPrestamosResponse> getEstadoPrestamosByIdCliente(Integer id);

    String guardarEstadoPrestamos(CalculadoraPagosRequest estadoPrestamosResponse);
}
