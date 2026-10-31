package com.crediexpress.dto.repository.spec;

import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Filtros combinables para buscar cuotas (patrón Specification). */
public final class CuotaSpecs {

    private static final String ESTADO_PAGO = "estadoPago";
    private static final String FECHA_VENCIMIENTO = "fechaVencimiento";
    private static final String CLIENTE = "idCliente";
    private static final String NOMBRE_CLIENTE = "nombreCompleto";

    private CuotaSpecs() {
    }

    /** Pendientes o con abono parcial ({@code NULL} cuenta como pendiente). */
    public static Specification<EstadoPrestamos> conSaldo() {
        return (cuota, consulta, cb) -> cb.or(
                cb.isNull(cuota.get(ESTADO_PAGO)),
                cuota.get(ESTADO_PAGO).in(EstadoCuota.codigosConSaldo()));
    }

    public static Specification<EstadoPrestamos> vencenAntesDe(LocalDate fecha) {
        return (cuota, consulta, cb) -> cb.lessThan(cuota.<LocalDate>get(FECHA_VENCIMIENTO), fecha);
    }

    public static Specification<EstadoPrestamos> vencenEntre(LocalDate desde, LocalDate hasta) {
        return (cuota, consulta, cb) -> cb.between(cuota.<LocalDate>get(FECHA_VENCIMIENTO), desde, hasta);
    }

    /** Nombre del cliente que contiene el texto; sin texto no filtra. */
    public static Specification<EstadoPrestamos> clienteContiene(String texto) {
        return (cuota, consulta, cb) -> Busqueda.contiene(cb, cuota.get(CLIENTE).<String>get(NOMBRE_CLIENTE), texto);
    }
}
