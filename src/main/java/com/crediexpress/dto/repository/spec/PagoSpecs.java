package com.crediexpress.dto.repository.spec;

import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.Pago;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** Filtros combinables para buscar pagos (patrón Specification). */
public final class PagoSpecs {

    private static final String FECHA_REGISTRO = "fechaRegistro";
    private static final String ESTATUS = "estatus";
    private static final String CLIENTE = "cliente";
    private static final String NOMBRE_CLIENTE = "nombreCompleto";

    private PagoSpecs() {
    }

    /** Registrados en [desde, hasta). */
    public static Specification<Pago> registradosEntre(Instant desde, Instant hasta) {
        return (pago, consulta, cb) -> cb.and(
                cb.greaterThanOrEqualTo(pago.<Instant>get(FECHA_REGISTRO), desde),
                cb.lessThan(pago.<Instant>get(FECHA_REGISTRO), hasta));
    }

    /** Con ese estatus; {@code null} = todos. */
    public static Specification<Pago> conEstatus(EstatusPago estatus) {
        return (pago, consulta, cb) -> estatus == null ? cb.conjunction() : cb.equal(pago.get(ESTATUS), estatus);
    }

    public static Specification<Pago> clienteContiene(String texto) {
        return (pago, consulta, cb) -> Busqueda.contiene(cb, pago.get(CLIENTE).<String>get(NOMBRE_CLIENTE), texto);
    }
}
