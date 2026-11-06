package com.crediexpress.service;

import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.RegistrarMovimientoCajaRequest;
import com.crediexpress.dto.view.response.MovimientoCajaResponse;
import com.crediexpress.dto.view.response.PaginaResponse;
import com.crediexpress.dto.view.response.SaldoCajaResponse;

/** Saldo de caja y sus movimientos (aportaciones y retiros). Contrato: docs/API-CAJA.md del front. */
public interface CajaService {

    /** @param anio {@code null} = toda la historia */
    SaldoCajaResponse saldo(Integer anio);

    /** Del más reciente al más antiguo, con los cancelados. */
    PaginaResponse<MovimientoCajaResponse> movimientos(int pagina, int tamano);

    MovimientoCajaResponse registrar(RegistrarMovimientoCajaRequest request, String usuario);

    MovimientoCajaResponse cancelar(Integer idMovimiento, CancelarPagoRequest request, String usuario);
}
