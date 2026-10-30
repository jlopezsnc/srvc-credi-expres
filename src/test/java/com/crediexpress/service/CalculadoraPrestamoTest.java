package com.crediexpress.service;

import com.crediexpress.service.CalculadoraPrestamo.Cuota;
import com.crediexpress.service.CalculadoraPrestamo.Periodicidad;
import com.crediexpress.service.CalculadoraPrestamo.Plan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Mismos casos que las pruebas del front (calculo-prestamo.spec.ts): ambos deben dar lo mismo. */
class CalculadoraPrestamoTest {

    private static final LocalDate PRIMER_PAGO = LocalDate.of(2026, 11, 5);

    private static Plan calcular(String monto, String tasa, int cuotas, Periodicidad periodicidad) {
        return CalculadoraPrestamo.calcular(new BigDecimal(monto), new BigDecimal(tasa), cuotas, periodicidad,
                PRIMER_PAGO);
    }

    @Test
    void cobraElInteresMensualPorCadaMesDelPlazo() {
        Plan plan = calcular("6000", "12", 12, Periodicidad.SEMANAL); // 12 semanas = 3 meses

        assertThat(plan.meses()).isEqualByComparingTo("3");
        assertThat(plan.interes()).isEqualTo(new BigDecimal("2160.00"));
        assertThat(plan.total()).isEqualTo(new BigDecimal("8160.00"));
        assertThat(plan.importeCuota()).isEqualTo(new BigDecimal("680.00"));
        assertThat(plan.cuotas()).extracting(Cuota::importe).containsOnly(new BigDecimal("680.00"));
    }

    @Test
    void cuentaDosQuincenasPorMes() {
        Plan plan = calcular("6000", "12", 8, Periodicidad.QUINCENAL); // 8 quincenas = 4 meses

        assertThat(plan.total()).isEqualTo(new BigDecimal("8880.00"));
        assertThat(plan.importeCuota()).isEqualTo(new BigDecimal("1110.00"));
    }

    @Test
    void laUltimaCuotaAbsorbeElAjusteDeCentavos() {
        Plan plan = calcular("6000", "12", 14, Periodicidad.QUINCENAL);

        assertThat(plan.total()).isEqualTo(new BigDecimal("11040.00"));
        assertThat(plan.importeCuota()).isEqualTo(new BigDecimal("788.57"));
        assertThat(plan.cuotas().getLast().importe()).isEqualTo(new BigDecimal("788.59"));
        BigDecimal suma = plan.cuotas().stream().map(Cuota::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualTo(plan.total());
    }

    @Test
    void aceptaMontosYTasasConDecimales() {
        Plan plan = calcular("1000.50", "7.5", 16, Periodicidad.SEMANAL); // 4 meses

        assertThat(plan.interes()).isEqualTo(new BigDecimal("300.15"));
        assertThat(plan.total()).isEqualTo(new BigDecimal("1300.65"));
    }

    @Test
    void lasFechasAvanzanSieteOQuinceDias() {
        assertThat(calcular("6000", "12", 12, Periodicidad.SEMANAL).cuotas())
                .extracting(Cuota::fechaVencimiento)
                .startsWith(PRIMER_PAGO, PRIMER_PAGO.plusDays(7), PRIMER_PAGO.plusDays(14));

        Plan quincenal = calcular("6000", "12", 8, Periodicidad.QUINCENAL);
        assertThat(quincenal.cuotas().get(1).fechaVencimiento()).isEqualTo(PRIMER_PAGO.plusDays(15));
        assertThat(quincenal.cuotas().getLast().fechaVencimiento()).isEqualTo(PRIMER_PAGO.plusDays(105));
        assertThat(quincenal.cuotas()).extracting(Cuota::numero).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void reconoceLaPeriodicidadPorLosDiasDeLaModalidad() {
        assertThat(Periodicidad.deDias(7)).contains(Periodicidad.SEMANAL);
        assertThat(Periodicidad.deDias(15)).contains(Periodicidad.QUINCENAL);
        assertThat(Periodicidad.deDias(30)).isEmpty();
        assertThat(Periodicidad.SEMANAL.permite(12)).isTrue();
        assertThat(Periodicidad.SEMANAL.permite(10)).isFalse();
        assertThat(Periodicidad.QUINCENAL.permite(14)).isTrue();
    }
}
