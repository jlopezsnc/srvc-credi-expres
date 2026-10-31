package com.crediexpress.service;

import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.SituacionCuota;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.OrdenCuotas;
import com.crediexpress.dto.view.response.CuotaCobranzaResponse;
import com.crediexpress.dto.view.response.OperacionPagoResponse;
import com.crediexpress.dto.view.response.PaginaConTotalResponse;
import com.crediexpress.dto.view.response.PaginaResponse;
import com.crediexpress.dto.view.response.PagoResponse;
import com.crediexpress.dto.view.response.ResumenCobranzaResponse;
import com.crediexpress.dto.view.response.SaldoPrestamoResponse;

import java.time.LocalDate;

/** Consultas de la pantalla de cobranza (solo lectura). */
public interface CobranzaConsultaService {

    /**
     * @param fecha {@code null} = hoy
     * @param dias  alcance de "próximas": de mañana a hoy + {@code dias}
     */
    ResumenCobranzaResponse resumen(LocalDate fecha, int dias);

    /** Una página de cuotas con saldo y el saldo total de todo lo filtrado. */
    PaginaConTotalResponse<CuotaCobranzaResponse> cuotasPendientes(SituacionCuota situacion, int dias,
                                                                   String busqueda, OrdenCuotas orden,
                                                                   Direccion direccion, int pagina, int tamano);

    /**
     * @param fecha   {@code null} = hoy
     * @param estatus {@code null} = todos
     */
    PaginaResponse<PagoResponse> pagos(LocalDate fecha, EstatusPago estatus, String busqueda, int pagina, int tamano);

    /** "Cobrado hoy": operaciones vigentes (un folio cada una) y lo que entró en caja. */
    PaginaConTotalResponse<OperacionPagoResponse> operaciones(LocalDate fecha, String busqueda, int pagina,
                                                              int tamano);

    SaldoPrestamoResponse saldoPrestamo(Integer idPrestamo);
}
