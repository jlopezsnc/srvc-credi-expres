package com.crediexpress.service.caja;

import com.crediexpress.dto.view.response.SaldoCajaResponse;
import com.crediexpress.util.Dinero;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Arma el saldo de caja con las sumas por mes. Recorre todos los meses desde el primero con
 * movimientos hasta el mes actual (o el último con movimientos, si es posterior), así el acumulado
 * siempre incluye todo lo anterior aunque se consulte un solo año.
 */
public final class SaldoCaja {

    private SaldoCaja() {
    }

    /** Sumas por mes de cada concepto. */
    public record Sumas(
            Map<YearMonth, BigDecimal> aportado,
            Map<YearMonth, BigDecimal> cobrado,
            Map<YearMonth, BigDecimal> prestado,
            Map<YearMonth, BigDecimal> retirado) {
    }

    /** @param anio {@code null} = toda la historia */
    public static SaldoCajaResponse armar(Sumas s, Integer anio, YearMonth mesActual) {
        TreeSet<YearMonth> conMovimientos = new TreeSet<>();
        Stream.of(s.aportado(), s.cobrado(), s.prestado(), s.retirado())
                .forEach(porMes -> conMovimientos.addAll(porMes.keySet()));
        List<Integer> anios = conMovimientos.stream().map(YearMonth::getYear).distinct().sorted().toList();

        List<SaldoCajaResponse.Mes> meses = new ArrayList<>();
        BigDecimal acumulado = Dinero.CERO;
        BigDecimal antesDelPeriodo = Dinero.CERO;
        if (!conMovimientos.isEmpty()) {
            YearMonth ultimo = conMovimientos.last().isAfter(mesActual) ? conMovimientos.last() : mesActual;
            for (YearMonth mes = conMovimientos.first(); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
                BigDecimal aportado = valor(s.aportado(), mes);
                BigDecimal cobrado = valor(s.cobrado(), mes);
                BigDecimal prestado = valor(s.prestado(), mes);
                BigDecimal retirado = valor(s.retirado(), mes);
                BigDecimal neto = aportado.add(cobrado).subtract(prestado).subtract(retirado);
                acumulado = acumulado.add(neto);
                if (anio != null && mes.getYear() < anio) {
                    antesDelPeriodo = acumulado;
                }
                if (anio == null || mes.getYear() == anio) {
                    meses.add(new SaldoCajaResponse.Mes(mes.toString(), aportado, cobrado, prestado, retirado,
                            neto, acumulado));
                }
            }
        }
        SaldoCajaResponse.Totales totales = new SaldoCajaResponse.Totales(
                suma(meses, SaldoCajaResponse.Mes::aportado), suma(meses, SaldoCajaResponse.Mes::cobrado),
                suma(meses, SaldoCajaResponse.Mes::prestado), suma(meses, SaldoCajaResponse.Mes::retirado),
                suma(meses, SaldoCajaResponse.Mes::neto));
        BigDecimal saldoFinal = meses.isEmpty() ? antesDelPeriodo : meses.getLast().acumulado();
        return new SaldoCajaResponse(anio, anios, totales, antesDelPeriodo, saldoFinal, acumulado, meses);
    }

    private static BigDecimal valor(Map<YearMonth, BigDecimal> porMes, YearMonth mes) {
        return Dinero.oCero(porMes.get(mes));
    }

    private static BigDecimal suma(List<SaldoCajaResponse.Mes> meses, Function<SaldoCajaResponse.Mes, BigDecimal> campo) {
        return meses.stream().map(campo).reduce(Dinero.CERO, BigDecimal::add);
    }
}
