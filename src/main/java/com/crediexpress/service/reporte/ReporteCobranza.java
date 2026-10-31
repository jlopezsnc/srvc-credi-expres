package com.crediexpress.service.reporte;

import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.view.response.OperacionPagoResponse;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse.Cancelados;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse.Dia;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse.Metodo;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse.Totales;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.OperacionesPago;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.crediexpress.service.cobranza.OperacionesPago.suma;
import static com.crediexpress.service.cobranza.OperacionesPago.totalRecibido;

/** Arma el reporte de cobranza a partir de los renglones de {@code pago} del periodo. */
public final class ReporteCobranza {

    private ReporteCobranza() {
    }

    /** @param pagos renglones del periodo en orden de registro, vigentes y cancelados */
    public static ReporteCobranzaResponse armar(PeriodoReporte periodo, List<Pago> pagos, CobranzaMapper mapper) {
        List<OperacionPagoResponse> operaciones = OperacionesPago.agrupar(pagos, mapper);
        List<OperacionPagoResponse> vigentes = operaciones.stream().filter(OperacionPagoResponse::vigente).toList();
        List<OperacionPagoResponse> canceladas = operaciones.stream()
                .filter(Predicate.not(OperacionPagoResponse::vigente))
                .toList();

        Map<LocalDate, List<OperacionPagoResponse>> porFecha = vigentes.stream()
                .collect(Collectors.groupingBy(o -> o.fechaRegistro().toLocalDate()));
        List<Dia> porDia = periodo.dias()
                .map(dia -> dia(dia, porFecha.getOrDefault(dia, List.of())))
                .toList();

        Map<MetodoPago, List<OperacionPagoResponse>> porMetodoPago = vigentes.stream()
                .collect(Collectors.groupingBy(OperacionPagoResponse::metodoPago));
        List<Metodo> porMetodo = Arrays.stream(MetodoPago.values())
                .map(metodo -> {
                    List<OperacionPagoResponse> lista = porMetodoPago.getOrDefault(metodo, List.of());
                    return new Metodo(metodo, lista.size(), totalRecibido(lista));
                })
                .toList();

        return new ReporteCobranzaResponse(
                periodo.desde(),
                periodo.hasta(),
                new Totales(vigentes.size(), suma(vigentes, OperacionPagoResponse::importe),
                        suma(vigentes, OperacionPagoResponse::recargo), totalRecibido(vigentes)),
                new Cancelados(canceladas.size(), totalRecibido(canceladas)),
                porDia,
                porMetodo,
                operaciones);
    }

    private static Dia dia(LocalDate fecha, List<OperacionPagoResponse> operaciones) {
        return new Dia(fecha, operaciones.size(), suma(operaciones, OperacionPagoResponse::importe),
                suma(operaciones, OperacionPagoResponse::recargo), totalRecibido(operaciones));
    }
}
