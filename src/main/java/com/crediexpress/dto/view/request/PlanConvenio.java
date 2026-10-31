package com.crediexpress.dto.view.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que define el plan de un convenio: lo comparten la simulación y la generación. */
public interface PlanConvenio {

    int MAXIMO_PRESTAMOS = 20;
    int MINIMO_PAGOS = 1;
    int MAXIMO_PAGOS = 52;

    List<Integer> idsPrestamo();

    Integer idModalidad();

    Integer numeroPagos();

    /** Interés mensual en porcentaje; 0 = sin interés (lo normal en un convenio). */
    BigDecimal tasaInteres();

    LocalDate fechaPrimerPago();
}
