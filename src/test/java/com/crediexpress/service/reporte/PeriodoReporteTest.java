package com.crediexpress.service.reporte;

import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodoReporteTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);
    private static final ZoneId MEXICO = ZoneId.of("America/Mexico_City");

    @Test
    void sinFechasVaDelDiaUnoDelMesAHoy() {
        assertThat(PeriodoReporte.de(null, null, HOY))
                .isEqualTo(new PeriodoReporte(LocalDate.of(2026, 10, 1), HOY));
    }

    @Test
    void completaLaFechaQueFalta() {
        assertThat(PeriodoReporte.de(LocalDate.of(2026, 10, 15), null, HOY).hasta()).isEqualTo(HOY);
        assertThat(PeriodoReporte.de(null, LocalDate.of(2026, 9, 20), HOY).desde())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(PeriodoReporte.de(LocalDate.of(2026, 11, 5), null, HOY).hasta())
                .isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void losLimitesSonMedianocheEnMexico() {
        PeriodoReporte periodo = new PeriodoReporte(HOY, HOY);
        assertThat(periodo.inicio(MEXICO)).isEqualTo(Instant.parse("2026-10-30T06:00:00Z"));
        assertThat(periodo.fin(MEXICO)).isEqualTo(Instant.parse("2026-10-31T06:00:00Z"));
        assertThat(periodo.dias()).containsExactly(HOY);
    }

    @Test
    void rechazaUnPeriodoAlReves() {
        assertThatThrownBy(() -> new PeriodoReporte(HOY, HOY.minusDays(1)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getMessage()).isEqualTo(PeriodoReporte.MENSAJE);
                    assertThat(error.getCampos()).extracting(CampoError::campo).containsExactly("desde");
                });
    }

    @Test
    void aceptaHastaNoventaYDosDias() {
        assertThat(new PeriodoReporte(HOY.minusDays(91), HOY).dias()).hasSize(92);
        assertThatThrownBy(() -> new PeriodoReporte(HOY.minusDays(92), HOY))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCampos())
                        .containsExactly(new CampoError("hasta", "El periodo no puede pasar de 92 días.")));
    }
}
