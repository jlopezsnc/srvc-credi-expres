package com.crediexpress.service.cobranza;

import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.view.request.SolicitudPago;
import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.exception.ApiException;
import com.crediexpress.util.Dinero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de un pago ya validados y normalizados (montos a centavos, textos recortados, sin
 * referencia en efectivo). Solo se crea con {@link #validar}: si existe, es válido.
 */
public record DatosPago(
        BigDecimal importe,
        BigDecimal recargo,
        LocalDate fechaPago,
        MetodoPago metodoPago,
        String referencia,
        String nota) {

    static final int DIAS_ATRAS_PERMITIDOS = 30;

    /**
     * Reglas que no cubre Bean Validation: rango de la fecha y referencia según el método.
     *
     * @param mensaje mensaje general del 400 ("Revisa los datos del pago.")
     */
    public static DatosPago validar(SolicitudPago solicitud, LocalDate hoy, String mensaje) {
        List<CampoError> errores = new ArrayList<>();
        if (solicitud.fechaPago().isAfter(hoy)) {
            errores.add(new CampoError("fechaPago", "La fecha del pago no puede ser posterior a hoy."));
        } else if (solicitud.fechaPago().isBefore(hoy.minusDays(DIAS_ATRAS_PERMITIDOS))) {
            errores.add(new CampoError("fechaPago",
                    "Solo se pueden registrar pagos de los últimos " + DIAS_ATRAS_PERMITIDOS + " días."));
        }
        String referencia = solicitud.metodoPago().requiereReferencia() ? limpiar(solicitud.referencia()) : null;
        if (solicitud.metodoPago().requiereReferencia() && referencia == null) {
            errores.add(new CampoError("referencia", "Captura la referencia del pago."));
        }
        if (!errores.isEmpty()) {
            throw ApiException.datosInvalidos(mensaje, errores);
        }
        return new DatosPago(Dinero.de(solicitud.importe()), Dinero.oCero(solicitud.recargo()),
                solicitud.fechaPago(), solicitud.metodoPago(), referencia, limpiar(solicitud.nota()));
    }

    /** El recargo por atraso solo se cobra si hay algo vencido. */
    public void validarRecargo(boolean hayVencidas, String mensaje, String mensajeCampo) {
        if (recargo.signum() > 0 && !hayVencidas) {
            throw ApiException.datosInvalidos(mensaje, List.of(new CampoError("recargo", mensajeCampo)));
        }
    }

    public BigDecimal totalRecibido() {
        return importe.add(recargo);
    }

    /** Representación estable para la huella de idempotencia. */
    String canonico() {
        return String.join("|", importe.toPlainString(), recargo.toPlainString(), fechaPago.toString(),
                metodoPago.name(), String.valueOf(referencia), String.valueOf(nota));
    }

    private static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String recortado = texto.strip();
        return recortado.isEmpty() ? null : recortado;
    }
}
