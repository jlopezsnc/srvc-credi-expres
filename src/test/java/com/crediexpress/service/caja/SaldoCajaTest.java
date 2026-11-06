package com.crediexpress.service.caja;

import com.crediexpress.dto.view.response.SaldoCajaResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SaldoCajaTest {

    private static final YearMonth HOY = YearMonth.of(2026, 2);

    // Dic-2025: aporta 10,000 y presta 8,000. Ene-2026: cobra 3,000 y retira 500. Feb-2026: sin nada.
    private static final SaldoCaja.Sumas SUMAS = new SaldoCaja.Sumas(
            Map.of(YearMonth.of(2025, 12), new BigDecimal("10000")),
            Map.of(YearMonth.of(2026, 1), new BigDecimal("3000")),
            Map.of(YearMonth.of(2025, 12), new BigDecimal("8000")),
            Map.of(YearMonth.of(2026, 1), new BigDecimal("500")));

    @Test
    void todaLaHistoriaSumaAportadoMasCobradoMenosPrestadoYRetirado() {
        SaldoCajaResponse r = SaldoCaja.armar(SUMAS, null, HOY);

        assertThat(r.anios()).containsExactly(2025, 2026);
        assertThat(r.meses()).extracting(SaldoCajaResponse.Mes::mes).containsExactly("2025-12", "2026-01", "2026-02");
        assertThat(r.meses()).extracting(SaldoCajaResponse.Mes::acumulado)
                .containsExactly(new BigDecimal("2000.00"), new BigDecimal("4500.00"), new BigDecimal("4500.00"));
        assertThat(r.totales().neto()).isEqualByComparingTo("4500");
        assertThat(r.saldoInicial()).isEqualByComparingTo("0");
        assertThat(r.saldoFinal()).isEqualByComparingTo("4500");
        assertThat(r.saldoActual()).isEqualByComparingTo("4500");
    }

    @Test
    void unAnioArrancaConLoAcumuladoAntes() {
        SaldoCajaResponse r = SaldoCaja.armar(SUMAS, 2026, HOY);

        assertThat(r.meses()).extracting(SaldoCajaResponse.Mes::mes).containsExactly("2026-01", "2026-02");
        assertThat(r.saldoInicial()).isEqualByComparingTo("2000");
        assertThat(r.totales().cobrado()).isEqualByComparingTo("3000");
        assertThat(r.totales().aportado()).isEqualByComparingTo("0");
        assertThat(r.totales().neto()).isEqualByComparingTo("2500");
        assertThat(r.saldoFinal()).isEqualByComparingTo("4500");
    }

    @Test
    void unAnioAnteriorCierraConSuPropioSaldo() {
        SaldoCajaResponse r = SaldoCaja.armar(SUMAS, 2025, HOY);

        assertThat(r.meses()).extracting(SaldoCajaResponse.Mes::mes).containsExactly("2025-12");
        assertThat(r.saldoFinal()).isEqualByComparingTo("2000");
        assertThat(r.saldoActual()).isEqualByComparingTo("4500");
    }

    @Test
    void sinMovimientosTodoEnCero() {
        SaldoCajaResponse r = SaldoCaja.armar(new SaldoCaja.Sumas(Map.of(), Map.of(), Map.of(), Map.of()), null, HOY);

        assertThat(r.meses()).isEmpty();
        assertThat(r.anios()).isEqualTo(List.of());
        assertThat(r.saldoActual()).isEqualByComparingTo("0");
    }
}
