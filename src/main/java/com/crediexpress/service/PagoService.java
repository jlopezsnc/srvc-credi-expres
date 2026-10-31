package com.crediexpress.service;

import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.LiquidarPrestamoRequest;
import com.crediexpress.dto.view.request.RegistrarPagoRequest;
import com.crediexpress.dto.view.response.LiquidacionResponse;
import com.crediexpress.dto.view.response.PagoResponse;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.ResultadoOperacion;

/** Operaciones que mueven dinero. {@code usuario} sale del token, nunca del cuerpo. */
public interface PagoService {

    ResultadoOperacion<PagoResponse> registrar(RegistrarPagoRequest request, LlaveIdempotencia llave, String usuario);

    ResultadoOperacion<LiquidacionResponse> liquidar(LiquidarPrestamoRequest request, LlaveIdempotencia llave,
                                                     String usuario);

    /** Cancela el pago y, si es de una liquidación, todos los renglones de su folio. */
    PagoResponse cancelar(Integer idPago, CancelarPagoRequest request, String usuario);
}
