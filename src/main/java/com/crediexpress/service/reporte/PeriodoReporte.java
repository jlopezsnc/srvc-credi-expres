package com.crediexpress.service.reporte;

import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.exception.ApiException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * Periodo de un reporte, ambos días incluidos. Solo existe si es válido: {@code desde <= hasta} y
 * a lo más {@value #MAXIMO_DIAS} días (un trimestre), para no traer años de pagos de una vez.
 */
public record PeriodoReporte(LocalDate desde, LocalDate hasta) {

    public static final int MAXIMO_DIAS = 92;
    public static final String MENSAJE = "Revisa el periodo del reporte.";

    public PeriodoReporte {
        if (desde.isAfter(hasta)) {
            throw invalido("desde", "La fecha inicial no puede ser posterior a la final.");
        }
        if (dias(desde, hasta) > MAXIMO_DIAS) {
            throw invalido("hasta", "El periodo no puede pasar de " + MAXIMO_DIAS + " días.");
        }
    }

    /**
     * Sin fechas: del día 1 del mes a hoy. Sin {@code hasta}: hasta hoy (o el mismo {@code desde} si
     * es posterior). Sin {@code desde}: el día 1 del mes de {@code hasta}.
     */
    public static PeriodoReporte de(LocalDate desde, LocalDate hasta, LocalDate hoy) {
        LocalDate fin = hasta != null ? hasta : (desde != null && desde.isAfter(hoy) ? desde : hoy);
        LocalDate inicio = desde != null ? desde : fin.withDayOfMonth(1);
        return new PeriodoReporte(inicio, fin);
    }

    /** Medianoche del primer día en la zona del negocio. */
    public Instant inicio(ZoneId zona) {
        return desde.atStartOfDay(zona).toInstant();
    }

    /** Medianoche del día siguiente al último (límite exclusivo). */
    public Instant fin(ZoneId zona) {
        return hasta.plusDays(1).atStartOfDay(zona).toInstant();
    }

    public Stream<LocalDate> dias() {
        return desde.datesUntil(hasta.plusDays(1));
    }

    private static long dias(LocalDate desde, LocalDate hasta) {
        return ChronoUnit.DAYS.between(desde, hasta) + 1;
    }

    private static ApiException invalido(String campo, String mensaje) {
        return ApiException.datosInvalidos(MENSAJE, List.of(new CampoError(campo, mensaje)));
    }
}
