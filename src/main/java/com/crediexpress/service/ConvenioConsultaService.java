package com.crediexpress.service;

import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroConvenios;
import com.crediexpress.dto.view.request.OrdenConvenios;
import com.crediexpress.dto.view.response.ConvenioDetalleResponse;
import com.crediexpress.dto.view.response.PaginaConveniosResponse;

/** Lista y detalle de convenios (solo lectura). Contrato: docs/API-CONVENIOS.md del front. */
public interface ConvenioConsultaService {

    PaginaConveniosResponse listar(FiltroConvenios filtro, String busqueda, OrdenConvenios orden,
                                   Direccion direccion, int pagina, int tamano);

    ConvenioDetalleResponse detalle(Integer idConvenio);
}
