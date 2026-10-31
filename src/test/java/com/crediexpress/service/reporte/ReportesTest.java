package com.crediexpress.service.reporte;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.entity.TipoPago;
import com.crediexpress.dto.view.response.ReporteCarteraResponse;
import com.crediexpress.dto.view.response.ReporteCarteraResponse.ClienteAtraso;
import com.crediexpress.dto.view.response.ReporteCarteraResponse.Rango;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse.Dia;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse.Metodo;
import com.crediexpress.dto.view.response.OperacionPagoResponse;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.CuotaConSaldo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.crediexpress.service.cobranza.CobranzaFixtures.HOY;
import static com.crediexpress.service.cobranza.CobranzaFixtures.cuota;
import static com.crediexpress.service.cobranza.CobranzaFixtures.prestamo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReportesTest {

    private final CobranzaMapper mapper = new CobranzaMapper();

    private static Cliente cliente(int id, String nombre) {
        return Cliente.builder().idCliente(id).nombreCompleto(nombre).telefono(null).build();
    }

    private static CalculadoraPrestamos prestamoDe(Cliente cliente, int id) {
        CalculadoraPrestamos prestamo = prestamo(cliente);
        ReflectionTestUtils.setField(prestamo, "idCalculadoraPrestamo", id);
        return prestamo;
    }

    @Nested
    class Cobranza {

        private final Cliente cliente = cliente(2, "CLIENTE DE PRUEBA");
        private final CalculadoraPrestamos prestamo = prestamoDe(cliente, 12);

        private Pago pago(int id, String folio, int numeroCuota, String importe, String recargo, MetodoPago metodo,
                          String registro, TipoPago tipo, EstatusPago estatus) {
            EstadoPrestamos cuota = cuota(prestamo, 100 + numeroCuota, numeroCuota, HOY, "1110.00",
                    EstadoCuota.PAGADA);
            Pago pago = Pago.builder()
                    .idPago(id)
                    .folio(folio)
                    .cuota(cuota)
                    .prestamo(prestamo)
                    .cliente(cliente)
                    .importe(new BigDecimal(importe))
                    .recargo(new BigDecimal(recargo))
                    .fechaPago(HOY)
                    .metodoPago(metodo)
                    .tipo(tipo)
                    .registradoPor("cobrador@ejemplo.com")
                    .fechaRegistro(Instant.parse(registro))
                    .build();
            ReflectionTestUtils.setField(pago, "estatus", estatus);
            return pago;
        }

        @Test
        void juntaLasLiquidacionesPorFolioYSoloSumaLoVigente() {
            PeriodoReporte periodo = new PeriodoReporte(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 30));
            List<Pago> pagos = List.of(
                    // 28 oct, 23:30 en México (29 oct en UTC): cuenta el 28.
                    pago(1, "P-000001", 2, "710.00", "50.00", MetodoPago.EFECTIVO, "2026-10-29T05:30:00Z",
                            TipoPago.CUOTA_COMPLETA, EstatusPago.VIGENTE),
                    pago(2, "P-000002", 3, "1110.00", "0.00", MetodoPago.TRANSFERENCIA, "2026-10-30T16:00:00Z",
                            TipoPago.LIQUIDACION, EstatusPago.VIGENTE),
                    pago(3, "P-000002", 4, "1110.00", "0.00", MetodoPago.TRANSFERENCIA, "2026-10-30T16:00:00Z",
                            TipoPago.LIQUIDACION, EstatusPago.VIGENTE),
                    pago(4, "P-000004", 5, "300.00", "0.00", MetodoPago.EFECTIVO, "2026-10-30T17:00:00Z",
                            TipoPago.ABONO_PARCIAL, EstatusPago.CANCELADO));

            ReporteCobranzaResponse reporte = ReporteCobranza.armar(periodo, pagos, mapper);

            assertThat(reporte.operaciones()).extracting(OperacionPagoResponse::folio)
                    .containsExactly("P-000001", "P-000002", "P-000004");
            OperacionPagoResponse liquidacion = reporte.operaciones().get(1);
            assertThat(liquidacion.cuotaInicial()).isEqualTo(3);
            assertThat(liquidacion.cuotaFinal()).isEqualTo(4);
            assertThat(liquidacion.totalRecibido()).isEqualByComparingTo("2220.00");

            assertThat(reporte.totales().operaciones()).isEqualTo(2);
            assertThat(reporte.totales().importe()).isEqualByComparingTo("2930.00");
            assertThat(reporte.totales().recargo()).isEqualByComparingTo("50.00");
            assertThat(reporte.totales().totalRecibido()).isEqualByComparingTo("2980.00");
            assertThat(reporte.cancelados().operaciones()).isEqualTo(1);
            assertThat(reporte.cancelados().totalRecibido()).isEqualByComparingTo("300.00");

            assertThat(reporte.porDia()).extracting(Dia::fecha, Dia::operaciones).containsExactly(
                    tuple(LocalDate.of(2026, 10, 28), 1L),
                    tuple(LocalDate.of(2026, 10, 29), 0L),
                    tuple(LocalDate.of(2026, 10, 30), 1L));
            assertThat(reporte.porMetodo()).extracting(Metodo::metodoPago)
                    .containsExactly(MetodoPago.EFECTIVO, MetodoPago.TRANSFERENCIA, MetodoPago.DEPOSITO);
            assertThat(reporte.porMetodo().get(0).totalRecibido()).isEqualByComparingTo("760.00");
            assertThat(reporte.porMetodo().get(2).operaciones()).isZero();
        }

        @Test
        void sinPagosLosTotalesSonCero() {
            ReporteCobranzaResponse reporte = ReporteCobranza.armar(new PeriodoReporte(HOY, HOY), List.of(), mapper);
            assertThat(reporte.totales().totalRecibido()).isEqualByComparingTo("0.00");
            assertThat(reporte.porDia()).hasSize(1);
            assertThat(reporte.operaciones()).isEmpty();
        }
    }

    @Nested
    class Cartera {

        private final Cliente ana = cliente(1, "ANA");
        private final Cliente beto = cliente(2, "BETO");
        private final CalculadoraPrestamos prestamoAna = prestamoDe(ana, 10);
        private final CalculadoraPrestamos prestamoBeto = prestamoDe(beto, 20);

        private CuotaConSaldo conSaldo(CalculadoraPrestamos prestamo, int id, LocalDate vence, String saldo) {
            EstadoPrestamos cuota = cuota(prestamo, id, id, vence, "1000.00", EstadoCuota.PENDIENTE);
            BigDecimal pendiente = new BigDecimal(saldo);
            return new CuotaConSaldo(cuota, new BigDecimal("1000.00").subtract(pendiente), pendiente);
        }

        @Test
        void separaVencidoDePorVencerYOrdenaPorAtraso() {
            List<CuotaConSaldo> cuotas = List.of(
                    conSaldo(prestamoAna, 1, HOY.minusDays(45), "1000.00"),
                    conSaldo(prestamoAna, 2, HOY.minusDays(3), "400.00"),
                    conSaldo(prestamoAna, 3, HOY.plusDays(10), "1000.00"),
                    conSaldo(prestamoBeto, 4, HOY.minusDays(70), "600.00"),
                    conSaldo(prestamoBeto, 5, HOY, "1000.00"),
                    // Cubierta por sus abonos: no cuenta.
                    conSaldo(prestamoBeto, 6, HOY.minusDays(1), "0.00"));

            ReporteCarteraResponse reporte = ReporteCartera.armar(HOY, cuotas, mapper);

            assertThat(reporte.totales().saldoTotal()).isEqualByComparingTo("4000.00");
            assertThat(reporte.totales().saldoVencido()).isEqualByComparingTo("2000.00");
            assertThat(reporte.totales().saldoPorVencer()).isEqualByComparingTo("2000.00");
            assertThat(reporte.totales().porcentajeVencido()).isEqualByComparingTo("50.0");
            assertThat(reporte.totales().cuotasVencidas()).isEqualTo(3);
            assertThat(reporte.totales().prestamosActivos()).isEqualTo(2);
            assertThat(reporte.totales().clientesConAtraso()).isEqualTo(2);

            assertThat(reporte.antiguedad()).extracting(Rango::etiqueta, Rango::cuotas).containsExactly(
                    tuple("1 a 7 días", 1),
                    tuple("8 a 15 días", 0),
                    tuple("16 a 30 días", 0),
                    tuple("31 a 60 días", 1),
                    tuple("Más de 60 días", 1));

            assertThat(reporte.clientesConAtraso()).extracting(c -> c.cliente().nombreCompleto())
                    .containsExactly("BETO", "ANA");
            ClienteAtraso anaAtraso = reporte.clientesConAtraso().get(1);
            assertThat(anaAtraso.cuotasVencidas()).isEqualTo(2);
            assertThat(anaAtraso.saldoVencido()).isEqualByComparingTo("1400.00");
            assertThat(anaAtraso.saldoTotal()).isEqualByComparingTo("2400.00");
            assertThat(anaAtraso.diasMaximoAtraso()).isEqualTo(45);
            assertThat(anaAtraso.vencimientoMasAntiguo()).isEqualTo(HOY.minusDays(45));
        }

        @Test
        void sinCarteraElPorcentajeEsCero() {
            ReporteCarteraResponse reporte = ReporteCartera.armar(HOY, List.of(), mapper);
            assertThat(reporte.totales().porcentajeVencido()).isEqualByComparingTo("0.0");
            assertThat(reporte.clientesConAtraso()).isEmpty();
        }
    }
}
