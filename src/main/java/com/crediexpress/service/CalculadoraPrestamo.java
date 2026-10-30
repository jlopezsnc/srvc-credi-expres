package com.crediexpress.service;

import com.crediexpress.util.Dinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Regla del préstamo acordada con el negocio (docs/API-PRESTAMOS.md del front):
 * <ol>
 *   <li>Plazo en meses = número de pagos / pagos por mes (12 semanas = 3 meses; 8 quincenas = 4).</li>
 *   <li>Interés = monto × interés mensual × plazo en meses.</li>
 *   <li>Total = monto + interés.</li>
 *   <li>Cuota = total / número de pagos; la última absorbe el ajuste de centavos.</li>
 * </ol>
 * El front calcula lo mismo solo para mostrarlo; lo que se guarda es lo que calcula esta clase.
 */
public final class CalculadoraPrestamo {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraPrestamo() {
    }

    /** Plazos permitidos por periodicidad, identificada por los días entre pagos de la modalidad. */
    public enum Periodicidad {
        SEMANAL(7, 4, List.of(12, 16)),
        QUINCENAL(15, 2, List.of(8, 10, 12, 14));

        private final int diasEntrePagos;
        private final int pagosPorMes;
        private final List<Integer> plazosPermitidos;

        Periodicidad(int diasEntrePagos, int pagosPorMes, List<Integer> plazosPermitidos) {
            this.diasEntrePagos = diasEntrePagos;
            this.pagosPorMes = pagosPorMes;
            this.plazosPermitidos = plazosPermitidos;
        }

        public static Optional<Periodicidad> deDias(Integer dias) {
            return java.util.Arrays.stream(values()).filter(p -> p.diasEntrePagos == dias).findFirst();
        }

        public int diasEntrePagos() {
            return diasEntrePagos;
        }

        public int pagosPorMes() {
            return pagosPorMes;
        }

        public List<Integer> plazosPermitidos() {
            return plazosPermitidos;
        }

        public boolean permite(int numeroCuotas) {
            return plazosPermitidos.contains(numeroCuotas);
        }
    }

    public record Cuota(int numero, LocalDate fechaVencimiento, BigDecimal importe) {
    }

    public record Plan(BigDecimal monto, BigDecimal tasaMensual, BigDecimal meses, BigDecimal interes,
                       BigDecimal total, BigDecimal importeCuota, List<Cuota> cuotas) {
    }

    public static Plan calcular(BigDecimal monto, BigDecimal tasaMensual, int numeroCuotas,
                                Periodicidad periodicidad, LocalDate primerPago) {
        BigDecimal montoExacto = Dinero.de(monto);
        BigDecimal meses = BigDecimal.valueOf(numeroCuotas)
                .divide(BigDecimal.valueOf(periodicidad.pagosPorMes()), 4, RoundingMode.HALF_UP);
        BigDecimal interes = Dinero.de(montoExacto.multiply(tasaMensual).multiply(meses).divide(CIEN, 8,
                RoundingMode.HALF_UP));
        BigDecimal total = montoExacto.add(interes);
        BigDecimal cuota = total.divide(BigDecimal.valueOf(numeroCuotas), Dinero.ESCALA, Dinero.REDONDEO);
        BigDecimal ultima = total.subtract(cuota.multiply(BigDecimal.valueOf(numeroCuotas - 1L)));

        List<Cuota> cuotas = IntStream.rangeClosed(1, numeroCuotas)
                .mapToObj(n -> new Cuota(
                        n,
                        primerPago.plusDays((long) periodicidad.diasEntrePagos() * (n - 1)),
                        n == numeroCuotas ? ultima : cuota))
                .toList();
        return new Plan(montoExacto, tasaMensual, meses, interes, total, cuota, cuotas);
    }
}
