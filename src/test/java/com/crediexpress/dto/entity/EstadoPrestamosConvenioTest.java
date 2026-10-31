package com.crediexpress.dto.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadoPrestamosConvenioTest {

    private static EstadoPrestamos cuota(EstadoCuota estado) {
        return EstadoPrestamos.builder().id(1).importeCuota(new BigDecimal("1110.00"))
                .fechaVencimiento(LocalDate.of(2026, 10, 30)).estadoPago(estado.codigo()).build();
    }

    @Test
    void unaCuotaEnConvenioYaNoTieneSaldoNiRecibeAbonos() {
        EstadoPrestamos cuota = cuota(EstadoCuota.ABONO_PARCIAL);

        cuota.pasarAConvenio();

        assertThat(cuota.estado()).isEqualTo(EstadoCuota.EN_CONVENIO);
        assertThat(cuota.saldo(new BigDecimal("400.00"))).isEqualByComparingTo("0");
        assertThatThrownBy(() -> cuota.aplicarAbonos(BigDecimal.ZERO, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soloSeConvienenCuotasConSaldo() {
        assertThatThrownBy(() -> cuota(EstadoCuota.PAGADA).pasarAConvenio()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cuota(EstadoCuota.EN_CONVENIO).pasarAConvenio())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void alCancelarElConvenioLasCuotasRegresanSegunSusAbonos() {
        EstadoPrestamos conAbono = cuota(EstadoCuota.EN_CONVENIO);
        EstadoPrestamos sinAbono = cuota(EstadoCuota.EN_CONVENIO);

        assertThat(conAbono.regresarDeConvenio(new BigDecimal("400.00"))).isEqualByComparingTo("710.00");
        sinAbono.regresarDeConvenio(BigDecimal.ZERO);

        assertThat(conAbono.estado()).isEqualTo(EstadoCuota.ABONO_PARCIAL);
        assertThat(sinAbono.estado()).isEqualTo(EstadoCuota.PENDIENTE);
        assertThatThrownBy(() -> cuota(EstadoCuota.PENDIENTE).regresarDeConvenio(BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void laCuotaDeUnConvenioCanceladoNoDebeNiRecibeAbonos() {
        EstadoPrestamos cuota = cuota(EstadoCuota.PENDIENTE);

        cuota.cancelar();

        assertThat(cuota.cancelada()).isTrue();
        assertThat(cuota.saldo(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> cuota.aplicarAbonos(BigDecimal.ONE, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cuota(EstadoCuota.ABONO_PARCIAL).cancelar()).isInstanceOf(IllegalStateException.class);
        assertThat(EstadoCuota.deCodigo(5)).isEqualTo(EstadoCuota.CANCELADA);
    }

    @Test
    void elCodigoCuatroEsEnConvenio() {
        assertThat(EstadoCuota.deCodigo(4)).isEqualTo(EstadoCuota.EN_CONVENIO);
        assertThat(EstadoCuota.codigosConSaldo()).doesNotContain(4);
    }
}
