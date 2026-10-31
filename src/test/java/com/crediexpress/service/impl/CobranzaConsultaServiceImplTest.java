package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.SituacionCuota;
import com.crediexpress.dto.repository.CalculadoraPrestamosRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository.TotalCuotas;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.repository.PagoRepository.TotalCobrado;
import com.crediexpress.dto.repository.consulta.CuotaPendienteRepository;
import com.crediexpress.dto.repository.consulta.CuotaPendienteRepository.Rango;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.OrdenCuotas;
import com.crediexpress.dto.view.response.PaginaConTotalResponse;
import com.crediexpress.dto.view.response.CuotaCobranzaResponse;
import com.crediexpress.dto.view.response.PaginaResponse;
import com.crediexpress.dto.view.response.ResumenCobranzaResponse;
import com.crediexpress.dto.view.response.SaldoPrestamoResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.SaldosCuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.crediexpress.service.cobranza.CobranzaFixtures.HOY;
import static com.crediexpress.service.cobranza.CobranzaFixtures.RELOJ;
import static com.crediexpress.service.cobranza.CobranzaFixtures.abono;
import static com.crediexpress.service.cobranza.CobranzaFixtures.cliente;
import static com.crediexpress.service.cobranza.CobranzaFixtures.cuota;
import static com.crediexpress.service.cobranza.CobranzaFixtures.prestamo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CobranzaConsultaServiceImplTest {

    @Mock
    private EstadoPrestamosRepository cuotaRepository;
    @Mock
    private CalculadoraPrestamosRepository prestamoRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private CuotaPendienteRepository cuotaPendienteRepository;
    @Captor
    private ArgumentCaptor<CuotaPendienteRepository.Consulta> consulta;

    private CobranzaConsultaServiceImpl servicio;
    private CalculadoraPrestamos prestamo;

    @BeforeEach
    void preparar() {
        servicio = new CobranzaConsultaServiceImpl(cuotaRepository, cuotaPendienteRepository, prestamoRepository,
                pagoRepository, new SaldosCuotas(pagoRepository), new CobranzaMapper(), RELOJ);
        prestamo = prestamo(cliente());
    }

    @Test
    void elResumenUsaSaldosYCuentaElDiaEnHoraDeMexico() {
        when(cuotaRepository.totalQueVence(HOY)).thenReturn(totalCuotas(3, 2135.0000001));
        when(cuotaRepository.totalVencidoAntesDe(HOY)).thenReturn(totalCuotas(0, null));
        when(cuotaRepository.totalQueVenceEntre(HOY.plusDays(1), HOY.plusDays(7))).thenReturn(totalCuotas(2, 1360.0));
        when(pagoRepository.totalCobrado(Instant.parse("2026-10-30T06:00:00Z"), Instant.parse("2026-10-31T06:00:00Z")))
                .thenReturn(totalCobrado(2, new BigDecimal("1088.57")));

        ResumenCobranzaResponse resumen = servicio.resumen(null, 7);

        assertThat(resumen.fecha()).isEqualTo(HOY);
        assertThat(resumen.porCobrarHoy().cuotas()).isEqualTo(3);
        assertThat(resumen.porCobrarHoy().importe()).isEqualTo(new BigDecimal("2135.00"));
        assertThat(resumen.vencido().importe()).isEqualTo(new BigDecimal("0.00"));
        assertThat(resumen.proximas().cuotas()).isEqualTo(2);
        assertThat(resumen.proximas().importe()).isEqualTo(new BigDecimal("1360.00"));
        assertThat(resumen.cobrado().pagos()).isEqualTo(2);
        assertThat(resumen.cobrado().importe()).isEqualTo(new BigDecimal("1088.57"));
    }

    @Test
    void lasCuotasPendientesPidenElRangoDeSuSituacionYCalculanLosDias() {
        CuotaPendienteRepository.Cuota vencida = filaCuota(101, HOY.minusDays(12), "400.00", "710.00");
        when(cuotaPendienteRepository.buscar(consulta.capture())).thenReturn(List.of(vencida));
        when(cuotaPendienteRepository.totalizar(any(), eq("martinez")))
                .thenReturn(new CuotaPendienteRepository.Total(1, new BigDecimal("710.00")));

        PaginaConTotalResponse<CuotaCobranzaResponse> respuesta = servicio.cuotasPendientes(SituacionCuota.VENCIDA, 7,
                "martinez", OrdenCuotas.SALDO, Direccion.DESC, 0, 10);

        assertThat(consulta.getValue()).isEqualTo(new CuotaPendienteRepository.Consulta(
                new Rango(null, HOY.minusDays(1)), "martinez", OrdenCuotas.SALDO, Direccion.DESC, 0, 10));
        assertThat(respuesta.totalElementos()).isEqualTo(1);
        assertThat(respuesta.importeTotal()).isEqualTo(new BigDecimal("710.00"));
        CuotaCobranzaResponse cuota = respuesta.contenido().getFirst();
        assertThat(cuota.importeAbonado()).isEqualTo(new BigDecimal("400.00"));
        assertThat(cuota.saldoCuota()).isEqualTo(new BigDecimal("710.00"));
        assertThat(cuota.diasAtraso()).isEqualTo(12);
        assertThat(cuota.diasParaVencer()).isZero();
        assertThat(cuota.situacion()).isEqualTo(SituacionCuota.VENCIDA);
        assertThat(cuota.totalCuotas()).isEqualTo(8);
        assertThat(cuota.modalidad().descripcion()).isEqualTo("Quincenal");
    }

    @Test
    void lasProximasDicenCuantosDiasFaltan() {
        when(cuotaPendienteRepository.buscar(consulta.capture()))
                .thenReturn(List.of(filaCuota(102, HOY.plusDays(3), "0.00", "1110.00")));
        when(cuotaPendienteRepository.totalizar(any(), any()))
                .thenReturn(new CuotaPendienteRepository.Total(1, new BigDecimal("1110.00")));

        CuotaCobranzaResponse cuota = servicio.cuotasPendientes(SituacionCuota.PROXIMA, 7, null, OrdenCuotas.VENCE,
                Direccion.ASC, 0, 10).contenido().getFirst();

        assertThat(consulta.getValue().rango()).isEqualTo(new Rango(HOY.plusDays(1), HOY.plusDays(7)));
        assertThat(cuota.diasParaVencer()).isEqualTo(3);
        assertThat(cuota.diasAtraso()).isZero();
        assertThat(cuota.situacion()).isEqualTo(SituacionCuota.PROXIMA);
    }

    private static CuotaPendienteRepository.Cuota filaCuota(int id, java.time.LocalDate vence, String abonado,
                                                            String saldo) {
        return new CuotaPendienteRepository.Cuota(id, 12, 2, "CLIENTE DE PRUEBA", "5500000000", 2, "Quincenal", 2, 8,
                vence, new BigDecimal("1110.00"), new BigDecimal(abonado), new BigDecimal(saldo));
    }

    @Test
    void elSaldoDelPrestamoSumaLasCuotasPendientes() {
        EstadoPrestamos vencida = cuota(prestamo, 101, 3, HOY.minusDays(13), "1110", EstadoCuota.ABONO_PARCIAL);
        EstadoPrestamos proxima = cuota(prestamo, 102, 4, HOY.plusDays(2), "1110", EstadoCuota.PENDIENTE);
        when(prestamoRepository.findById(12)).thenReturn(Optional.of(prestamo));
        when(cuotaRepository.findConSaldoDelPrestamo(12)).thenReturn(List.of(vencida, proxima));
        when(pagoRepository.sumarVigentesPorCuota(List.of(101, 102))).thenReturn(List.of(abono(101, "400")));

        SaldoPrestamoResponse saldo = servicio.saldoPrestamo(12);

        assertThat(saldo.saldoTotal()).isEqualTo(new BigDecimal("1820.00"));
        assertThat(saldo.cuotasPendientes()).isEqualTo(2);
        assertThat(saldo.hayCuotasVencidas()).isTrue();
        assertThat(saldo.cuotas()).extracting(SaldoPrestamoResponse.Cuota::situacion)
                .containsExactly(SituacionCuota.VENCIDA, SituacionCuota.PROXIMA);
    }

    @Test
    void elSaldoDeUnPrestamoLiquidadoResponde409() {
        when(prestamoRepository.findById(12)).thenReturn(Optional.of(prestamo));
        when(cuotaRepository.findConSaldoDelPrestamo(12)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.saldoPrestamo(12))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getMessage()).isEqualTo("El préstamo ya está liquidado.");
                });
        verify(pagoRepository, never()).sumarVigentesPorCuota(any());
    }

    private static TotalCuotas totalCuotas(long cuotas, Double importe) {
        return new TotalCuotas() {
            @Override
            public Long getCuotas() {
                return cuotas;
            }

            @Override
            public Number getImporte() {
                return importe;
            }
        };
    }

    private static TotalCobrado totalCobrado(long pagos, BigDecimal importe) {
        return new TotalCobrado() {
            @Override
            public Long getPagos() {
                return pagos;
            }

            @Override
            public Number getImporte() {
                return importe;
            }
        };
    }
}
