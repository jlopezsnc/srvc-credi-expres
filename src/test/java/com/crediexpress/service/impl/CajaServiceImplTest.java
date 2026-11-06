package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.EstatusMovimientoCaja;
import com.crediexpress.dto.entity.MovimientoCaja;
import com.crediexpress.dto.entity.TipoMovimientoCaja;
import com.crediexpress.dto.repository.MovimientoCajaRepository;
import com.crediexpress.dto.repository.consulta.CajaRepository;
import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.RegistrarMovimientoCajaRequest;
import com.crediexpress.dto.view.response.MovimientoCajaResponse;
import com.crediexpress.dto.view.response.SaldoCajaResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.caja.MensajesCaja;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaServiceImplTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-05T18:00:00Z"),
            ZoneId.of("America/Mexico_City"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);
    private static final String USUARIO = "user";

    @Mock
    private CajaRepository cajaRepository;
    @Mock
    private MovimientoCajaRepository movimientoRepository;

    private CajaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new CajaServiceImpl(cajaRepository, movimientoRepository, RELOJ);
    }

    @Test
    void elSaldoJuntaLasCuatroSumas() {
        YearMonth octubre = YearMonth.of(2026, 10);
        when(cajaRepository.aportadoPorMes()).thenReturn(Map.of(octubre, new BigDecimal("50000")));
        when(cajaRepository.cobradoPorMes()).thenReturn(Map.of(octubre, new BigDecimal("12000")));
        when(cajaRepository.prestadoPorMes()).thenReturn(Map.of(octubre, new BigDecimal("30000")));
        when(cajaRepository.retiradoPorMes()).thenReturn(Map.of(octubre, new BigDecimal("2000")));

        SaldoCajaResponse saldo = servicio.saldo(null);

        assertThat(saldo.saldoActual()).isEqualByComparingTo("30000");
        // Llega hasta el mes actual (noviembre) aunque no tenga movimientos.
        assertThat(saldo.meses()).extracting(SaldoCajaResponse.Mes::mes).containsExactly("2026-10", "2026-11");
    }

    @Test
    void unAnioFueraDeRangoEs400() {
        assertThatThrownBy(() -> servicio.saldo(1999))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(cajaRepository);
    }

    @Test
    void registraConElUsuarioYElConceptoRecortado() {
        when(movimientoRepository.save(any(MovimientoCaja.class))).thenAnswer(i -> i.getArgument(0));

        MovimientoCajaResponse r = servicio.registrar(new RegistrarMovimientoCajaRequest(TipoMovimientoCaja.APORTACION,
                new BigDecimal("50000"), HOY, "  Capital inicial  "), USUARIO);

        assertThat(r.tipo()).isEqualTo(TipoMovimientoCaja.APORTACION);
        assertThat(r.importe()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(r.concepto()).isEqualTo("Capital inicial");
        assertThat(r.registradoPor()).isEqualTo(USUARIO);
        assertThat(r.fechaRegistro()).isEqualTo(Instant.now(RELOJ));
        assertThat(r.estatus()).isEqualTo(EstatusMovimientoCaja.VIGENTE);
    }

    @Test
    void noAceptaFechasFuturas() {
        assertThatThrownBy(() -> servicio.registrar(new RegistrarMovimientoCajaRequest(TipoMovimientoCaja.RETIRO,
                BigDecimal.TEN, HOY.plusDays(1), "Retiro"), USUARIO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCampos()).extracting("mensaje").containsExactly(MensajesCaja.FECHA_FUTURA);
                });
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void cancelaSinBorrar() {
        MovimientoCaja retiro = MovimientoCaja.builder().idMovimiento(7).tipo(TipoMovimientoCaja.RETIRO)
                .importe(new BigDecimal("500.00")).fecha(HOY).concepto("Gasolina").registradoPor(USUARIO)
                .fechaRegistro(Instant.now(RELOJ)).build();
        when(movimientoRepository.findById(7)).thenReturn(Optional.of(retiro));

        MovimientoCajaResponse r = servicio.cancelar(7, new CancelarPagoRequest(" Capturado dos veces "), USUARIO);

        assertThat(r.estatus()).isEqualTo(EstatusMovimientoCaja.CANCELADO);
        assertThat(r.motivoCancelacion()).isEqualTo("Capturado dos veces");
        assertThat(r.canceladoPor()).isEqualTo(USUARIO);

        assertThatThrownBy(() -> servicio.cancelar(7, new CancelarPagoRequest("Otra vez"), USUARIO))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelarUnoQueNoExisteEs404() {
        when(movimientoRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cancelar(99, new CancelarPagoRequest("No existe"), USUARIO))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
