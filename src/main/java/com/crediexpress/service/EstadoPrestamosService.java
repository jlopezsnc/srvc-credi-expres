package com.crediexpress.service;
import com.crediexpress.dto.view.request.CalculadoraPagosRequest;
import com.crediexpress.dto.view.response.CalcularPagoResponse;
import com.crediexpress.dto.view.response.EstadoPrestamosResponse;

import java.util.List;


public interface EstadoPrestamosService {

    List<EstadoPrestamosResponse> getEstadosPrestamos();

    EstadoPrestamosResponse getEstadoPrestamosById(Integer id);

    List<EstadoPrestamosResponse> getEstadoPrestamosByIdCliente(Integer id);

    /** Calcula y guarda el préstamo con su calendario de cuotas, todo o nada. */
    CalcularPagoResponse guardarEstadoPrestamos(CalculadoraPagosRequest request);
}
