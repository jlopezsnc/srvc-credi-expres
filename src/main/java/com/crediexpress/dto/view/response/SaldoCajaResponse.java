package com.crediexpress.dto.view.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Saldo de caja: aportado + cobrado - prestado - retirado.
 *
 * @param anio         año consultado; {@code null} = toda la historia
 * @param anios        años con movimientos, para elegir
 * @param saldoInicial lo que había al empezar el periodo (0 en toda la historia)
 * @param saldoFinal   lo que debía haber al cerrar el periodo
 * @param saldoActual  lo que debe haber hoy, con toda la historia
 * @param meses        del más antiguo al más reciente; los meses sin movimientos van en 0
 */
public record SaldoCajaResponse(
        Integer anio,
        List<Integer> anios,
        Totales totales,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoInicial,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoFinal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoActual,
        List<Mes> meses) {

    /** Sumas del periodo; {@code neto} = aportado + cobrado - prestado - retirado. */
    public record Totales(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal aportado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cobrado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal prestado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal retirado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal neto) {
    }

    /** @param mes {@code yyyy-MM}; {@code acumulado} = saldo al cierre del mes, con todo lo anterior */
    public record Mes(
            String mes,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal aportado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cobrado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal prestado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal retirado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal neto,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal acumulado) {
    }
}
