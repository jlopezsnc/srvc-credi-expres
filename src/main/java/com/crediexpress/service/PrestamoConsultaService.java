package com.crediexpress.service;

import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import com.crediexpress.dto.view.response.PaginaPrestamosResponse;
import com.crediexpress.dto.view.response.PrestamoDetalleResponse;

/** Lista de préstamos y su calendario (solo lectura). Contrato: docs/API-PRESTAMOS.md del front. */
public interface PrestamoConsultaService {

    PaginaPrestamosResponse listar(FiltroPrestamos filtro, String busqueda, OrdenPrestamos orden, Direccion direccion,
                                   int pagina, int tamano);

    PrestamoDetalleResponse detalle(Integer idPrestamo);
}
