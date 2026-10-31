package com.crediexpress.service.cobranza;

import com.crediexpress.config.ClockConfig;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.view.response.OperacionPagoResponse;
import com.crediexpress.util.Dinero;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Junta los renglones de {@code pago} en operaciones (un folio cada una), respetando el orden recibido. */
public final class OperacionesPago {

    private OperacionesPago() {
    }

    public static List<OperacionPagoResponse> agrupar(List<Pago> renglones, CobranzaMapper mapper) {
        return renglones.stream()
                .collect(Collectors.groupingBy(Pago::getFolio, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(grupo -> operacion(grupo, mapper))
                .toList();
    }

    public static BigDecimal totalRecibido(Collection<OperacionPagoResponse> operaciones) {
        return suma(operaciones, OperacionPagoResponse::totalRecibido);
    }

    public static BigDecimal suma(Collection<OperacionPagoResponse> operaciones,
                                  Function<OperacionPagoResponse, BigDecimal> campo) {
        return operaciones.stream().map(campo).reduce(Dinero.CERO, BigDecimal::add);
    }

    private static OperacionPagoResponse operacion(List<Pago> renglones, CobranzaMapper mapper) {
        Pago primero = renglones.getFirst();
        BigDecimal importe = sumaPagos(renglones, Pago::getImporte);
        BigDecimal recargo = sumaPagos(renglones, Pago::getRecargo);
        int cuotaInicial = renglones.stream().mapToInt(p -> p.getCuota().getNumeroCuota()).min().orElse(0);
        int cuotaFinal = renglones.stream().mapToInt(p -> p.getCuota().getNumeroCuota()).max().orElse(0);
        return new OperacionPagoResponse(
                primero.getFolio(),
                primero.getFechaRegistro().atZone(ClockConfig.ZONA_NEGOCIO).toOffsetDateTime(),
                primero.getFechaPago(),
                mapper.cliente(primero.getCliente()),
                primero.getPrestamo().getIdCalculadoraPrestamo(),
                cuotaInicial,
                cuotaFinal,
                primero.getPrestamo().getNumeroCuotas(),
                primero.getTipo(),
                primero.getMetodoPago(),
                primero.getReferencia(),
                importe,
                recargo,
                importe.add(recargo),
                primero.getRegistradoPor(),
                primero.getEstatus());
    }

    private static BigDecimal sumaPagos(Collection<Pago> pagos, Function<Pago, BigDecimal> campo) {
        return pagos.stream().map(campo).map(Dinero::oCero).reduce(Dinero.CERO, BigDecimal::add);
    }
}
