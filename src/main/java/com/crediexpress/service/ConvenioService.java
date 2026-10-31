package com.crediexpress.service;

import com.crediexpress.dto.view.request.CancelarConvenioRequest;
import com.crediexpress.dto.view.request.GenerarConvenioRequest;
import com.crediexpress.dto.view.request.SimularConvenioRequest;
import com.crediexpress.dto.view.response.ConvenioDetalleResponse;
import com.crediexpress.dto.view.response.ConvenioResponse;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.ResultadoOperacion;

/** Operaciones que cambian convenios: simular, generar y cancelar. Contrato: docs/API-CONVENIOS.md del front. */
public interface ConvenioService {

    SimulacionConvenioResponse simular(SimularConvenioRequest request);

    /**
     * @param usuario  quien lo genera (del token), para la bitácora
     * @param ipOrigen IP de la petición, para la bitácora
     */
    ResultadoOperacion<ConvenioResponse> generar(GenerarConvenioRequest request, LlaveIdempotencia llave,
                                                 String usuario, String ipOrigen);

    /**
     * Cancela un convenio vigente sin pagos: los préstamos de origen vuelven a cobrarse como estaban
     * y el préstamo del convenio queda cancelado ({@code estado_pago = 5}).
     *
     * @return el detalle ya cancelado
     */
    ConvenioDetalleResponse cancelar(Integer idConvenio, CancelarConvenioRequest request, String usuario);
}
