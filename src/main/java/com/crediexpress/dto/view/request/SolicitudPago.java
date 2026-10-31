package com.crediexpress.dto.view.request;

import com.crediexpress.dto.entity.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos comunes a cobrar una cuota y a liquidar un préstamo. */
public interface SolicitudPago {

    BigDecimal importe();

    BigDecimal recargo();

    LocalDate fechaPago();

    MetodoPago metodoPago();

    String referencia();

    String nota();
}
