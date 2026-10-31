package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.entity.TipoPago;
import com.crediexpress.dto.repository.CalculadoraPrestamosRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.LiquidarPrestamoRequest;
import com.crediexpress.dto.view.request.RegistrarPagoRequest;
import com.crediexpress.dto.view.response.LiquidacionResponse;
import com.crediexpress.dto.view.response.PagoResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.DatosPago;
import com.crediexpress.service.cobranza.IdempotenciaPago;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.OperacionPago;
import com.crediexpress.service.cobranza.ResultadoOperacion;
import com.crediexpress.service.cobranza.SaldosCuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.crediexpress.service.cobranza.CobranzaFixtures.HOY;
import static com.crediexpress.service.cobranza.CobranzaFixtures.LLAVE;
import static com.crediexpress.service.cobranza.CobranzaFixtures.RELOJ;
import static com.crediexpress.service.cobranza.CobranzaFixtures.abono;
import static com.crediexpress.service.cobranza.CobranzaFixtures.cliente;
import static com.crediexpress.service.cobranza.CobranzaFixtures.conId;
import static com.crediexpress.service.cobranza.CobranzaFixtures.cuota;
import static com.crediexpress.service.cobranza.CobranzaFixtures.prestamo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    private static final String USUARIO = "cobrador@ejemplo.com";
    private static final LlaveIdempotencia LLAVE_PAGO = LlaveIdempotencia.de(LLAVE);

    @Mock
    private EstadoPrestamosRepository cuotaRepository;
    @Mock
    private CalculadoraPrestamosRepository prestamoRepository;
    @Mock
    private PagoRepository pagoRepository;

    private PagoServiceImpl servicio;
    private CalculadoraPrestamos prestamo;
    /** Vencida hace 13 días. */
    private EstadoPrestamos cuota1;
    /** Vence en 2 días. */
    private EstadoPrestamos cuota2;
    private final AtomicInteger siguienteId = new AtomicInteger(233);

    @BeforeEach
    void preparar() {
        servicio = new PagoServiceImpl(cuotaRepository, prestamoRepository, pagoRepository,
                new IdempotenciaPago(pagoRepository), new SaldosCuotas(pagoRepository), new CobranzaMapper(), RELOJ);
        prestamo = prestamo(cliente());
        cuota1 = cuota(prestamo, 101, 1, HOY.minusDays(13), "1110", EstadoCuota.PENDIENTE);
        cuota2 = cuota(prestamo, 102, 2, HOY.plusDays(2), "1110", EstadoCuota.PENDIENTE);
    }

    private void guardarConId() {
        when(pagoRepository.save(any())).thenAnswer(invocacion ->
                conId(invocacion.<Pago>getArgument(0), "idPago", siguienteId.getAndIncrement()));
    }

    private void conCuotasPendientes(EstadoPrestamos... cuotas) {
        when(cuotaRepository.bloquearConSaldoDelPrestamo(12)).thenReturn(List.of(cuotas));
    }

    @Nested
    class Registrar {

        private RegistrarPagoRequest request(int idCuota, String importe, String recargo, MetodoPago metodo,
                                             String referencia) {
            return new RegistrarPagoRequest(idCuota, new BigDecimal(importe),
                    recargo == null ? null : new BigDecimal(recargo), HOY, metodo, referencia, "  Pagó en su domicilio ");
        }

        private ResultadoOperacion<PagoResponse> registrar(RegistrarPagoRequest request) {
            return servicio.registrar(request, LLAVE_PAGO, USUARIO);
        }

        @BeforeEach
        void cuotaExiste() {
            // No todas las pruebas cobran la cuota 101.
            lenient().when(cuotaRepository.findIdPrestamo(101)).thenReturn(Optional.of(12));
        }

        @Test
        void cobraLaCuotaCompletaConRecargo() {
            conCuotasPendientes(cuota1, cuota2);
            guardarConId();

            ResultadoOperacion<PagoResponse> resultado =
                    registrar(request(101, "1110.00", "50", MetodoPago.TRANSFERENCIA, " 0045821933 "));

            assertThat(resultado.repetida()).isFalse();
            PagoResponse pago = resultado.valor();
            assertThat(pago.folio()).isEqualTo("P-000233");
            assertThat(pago.tipo()).isEqualTo(TipoPago.CUOTA_COMPLETA);
            assertThat(pago.importe()).isEqualTo(new BigDecimal("1110.00"));
            assertThat(pago.recargo()).isEqualTo(new BigDecimal("50.00"));
            assertThat(pago.totalRecibido()).isEqualTo(new BigDecimal("1160.00"));
            assertThat(pago.saldoCuota()).isEqualTo(new BigDecimal("0.00"));
            assertThat(pago.cuotaLiquidada()).isTrue();
            assertThat(pago.prestamoLiquidado()).isFalse();
            assertThat(pago.referencia()).isEqualTo("0045821933");
            assertThat(pago.nota()).isEqualTo("Pagó en su domicilio");
            assertThat(pago.registradoPor()).isEqualTo(USUARIO);
            assertThat(pago.numeroCuota()).isEqualTo(1);
            assertThat(pago.totalCuotas()).isEqualTo(8);
            assertThat(pago.fechaRegistro()).hasToString("2026-10-30T12:00-06:00");
            assertThat(pago.estatus()).isEqualTo(EstatusPago.VIGENTE);
            assertThat(cuota1.estado()).isEqualTo(EstadoCuota.PAGADA);
            assertThat(cuota1.getFechaPago()).isEqualTo(HOY);
        }

        @Test
        void unAbonoParcialDejaSaldoEnLaMismaCuota() {
            conCuotasPendientes(cuota1, cuota2);
            guardarConId();

            PagoResponse pago = registrar(request(101, "400", null, MetodoPago.EFECTIVO, "no se guarda")).valor();

            assertThat(pago.tipo()).isEqualTo(TipoPago.ABONO_PARCIAL);
            assertThat(pago.saldoCuota()).isEqualTo(new BigDecimal("710.00"));
            assertThat(pago.cuotaLiquidada()).isFalse();
            assertThat(pago.referencia()).isNull();
            assertThat(cuota1.estado()).isEqualTo(EstadoCuota.ABONO_PARCIAL);
            assertThat(cuota1.getFechaPago()).isNull();
        }

        @Test
        void completarElUltimoSaldoLiquidaElPrestamo() {
            cuota1.setEstadoPago(EstadoCuota.ABONO_PARCIAL.codigo());
            conCuotasPendientes(cuota1);
            when(pagoRepository.sumarVigentesPorCuota(List.of(101))).thenReturn(List.of(abono(101, "400.00")));
            guardarConId();

            PagoResponse pago = registrar(request(101, "710", null, MetodoPago.EFECTIVO, null)).valor();

            assertThat(pago.tipo()).isEqualTo(TipoPago.CUOTA_COMPLETA);
            assertThat(pago.prestamoLiquidado()).isTrue();
            assertThat(cuota1.estado()).isEqualTo(EstadoCuota.PAGADA);
        }

        @Test
        void noPermitePagarMasQueElSaldo() {
            conCuotasPendientes(cuota1);
            when(pagoRepository.sumarVigentesPorCuota(List.of(101))).thenReturn(List.of(abono(101, "400.00")));

            assertThatThrownBy(() -> registrar(request(101, "710.01", null, MetodoPago.EFECTIVO, null)))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(error.getMessage()).isEqualTo("El importe supera el saldo de la cuota ($710.00). "
                                + "Puede que alguien haya registrado un pago antes.");
                    });
            verify(pagoRepository, never()).save(any());
        }

        @Test
        void exigeCobrarPrimeroLaCuotaMasAntigua() {
            when(cuotaRepository.findIdPrestamo(102)).thenReturn(Optional.of(12));
            conCuotasPendientes(cuota1, cuota2);

            assertThatThrownBy(() -> registrar(request(102, "1110", null, MetodoPago.EFECTIVO, null)))
                    .isInstanceOfSatisfying(ApiException.class, error ->
                            assertThat(error.getMessage()).isEqualTo("Primero hay que cobrar la cuota 1 de este préstamo."));
        }

        @Test
        void unaCuotaPagadaNoSePuedeVolverACobrar() {
            conCuotasPendientes(cuota2);

            assertThatThrownBy(() -> registrar(request(101, "1110", null, MetodoPago.EFECTIVO, null)))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(error.getMessage()).isEqualTo("Esta cuota ya está pagada.");
                    });
        }

        @Test
        void cuotaInexistenteResponde404() {
            when(cuotaRepository.findIdPrestamo(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> registrar(request(999, "10", null, MetodoPago.EFECTIVO, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void noCobraRecargoAUnaCuotaQueNoHaVencido() {
            when(cuotaRepository.findIdPrestamo(102)).thenReturn(Optional.of(12));
            conCuotasPendientes(cuota2);

            assertThatThrownBy(() -> registrar(request(102, "1110", "50", MetodoPago.EFECTIVO, null)))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(error.getCampos()).singleElement().satisfies(campo ->
                                assertThat(campo.mensaje()).isEqualTo("Solo se puede cobrar recargo a una cuota vencida."));
                    });
        }

        @Test
        void laMismaPeticionRepetidaDevuelveElPagoOriginalSinCrearOtro() {
            RegistrarPagoRequest request = request(101, "1110", null, MetodoPago.EFECTIVO, null);
            Pago original = pagoPrevio(request);
            conCuotasPendientes(cuota2);
            when(pagoRepository.findByLlaveIdempotenciaOrderByIdPago(LLAVE)).thenReturn(List.of(original));

            ResultadoOperacion<PagoResponse> resultado = registrar(request);

            assertThat(resultado.repetida()).isTrue();
            assertThat(resultado.valor().folio()).isEqualTo("P-000200");
            verify(pagoRepository, never()).save(any());
        }

        @Test
        void laMismaLlaveConOtrosDatosResponde409() {
            Pago original = pagoPrevio(request(101, "1110", null, MetodoPago.EFECTIVO, null));
            conCuotasPendientes(cuota1);
            when(pagoRepository.findByLlaveIdempotenciaOrderByIdPago(LLAVE)).thenReturn(List.of(original));

            assertThatThrownBy(() -> registrar(request(101, "500", null, MetodoPago.EFECTIVO, null)))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(error.getMessage()).startsWith("Este identificador ya se usó para otro pago.");
                    });
        }

        private Pago pagoPrevio(RegistrarPagoRequest request) {
            DatosPago datos = DatosPago.validar(request, HOY, RegistrarPagoRequest.MENSAJE);
            String huella = IdempotenciaPago.huella(IdempotenciaPago.Operacion.REGISTRAR_PAGO, request.idCuota(), datos);
            Pago pago = conId(new OperacionPago(LLAVE_PAGO, huella, datos, USUARIO, RELOJ.instant())
                    .renglon(cuota1, datos.importe(), datos.recargo(), TipoPago.CUOTA_COMPLETA,
                            new BigDecimal("0.00"), false), "idPago", 200);
            pago.asignarFolio();
            return pago;
        }
    }

    @Nested
    class Liquidar {

        private LiquidarPrestamoRequest request(String importe, String recargo) {
            return new LiquidarPrestamoRequest(12, new BigDecimal(importe),
                    recargo == null ? null : new BigDecimal(recargo), HOY, MetodoPago.EFECTIVO, null, "Liquidó");
        }

        @BeforeEach
        void prestamoExiste() {
            when(prestamoRepository.existsById(12)).thenReturn(true);
        }

        @Test
        void creaUnRenglonPorCuotaConElMismoFolioYDejaTodoPagado() {
            cuota1.setEstadoPago(EstadoCuota.ABONO_PARCIAL.codigo());
            conCuotasPendientes(cuota1, cuota2);
            when(pagoRepository.sumarVigentesPorCuota(List.of(101, 102))).thenReturn(List.of(abono(101, "400")));
            when(cuotaRepository.clienteTieneSaldo(2)).thenReturn(false);
            guardarConId();

            ResultadoOperacion<LiquidacionResponse> resultado =
                    servicio.liquidar(request("1820.00", "150"), LLAVE_PAGO, USUARIO);

            LiquidacionResponse liquidacion = resultado.valor();
            assertThat(resultado.repetida()).isFalse();
            assertThat(liquidacion.folio()).isEqualTo("P-000233");
            assertThat(liquidacion.cuotasLiquidadas()).isEqualTo(2);
            assertThat(liquidacion.importe()).isEqualTo(new BigDecimal("1820.00"));
            assertThat(liquidacion.recargo()).isEqualTo(new BigDecimal("150.00"));
            assertThat(liquidacion.totalRecibido()).isEqualTo(new BigDecimal("1970.00"));
            assertThat(liquidacion.pagos()).extracting(LiquidacionResponse.Renglon::importe)
                    .containsExactly(new BigDecimal("710.00"), new BigDecimal("1110.00"));
            assertThat(liquidacion.prestamoLiquidado()).isTrue();
            assertThat(liquidacion.clienteSinAdeudos()).isTrue();
            assertThat(cuota1.estado()).isEqualTo(EstadoCuota.PAGADA);
            assertThat(cuota2.estado()).isEqualTo(EstadoCuota.PAGADA);
            assertThat(cuota2.getFechaPago()).isEqualTo(HOY);
        }

        @Test
        void elImporteDebeSerExactamenteElSaldo() {
            conCuotasPendientes(cuota1, cuota2);

            assertThatThrownBy(() -> servicio.liquidar(request("2000", null), LLAVE_PAGO, USUARIO))
                    .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getMessage())
                            .isEqualTo("El importe no coincide con el saldo del préstamo ($2,220.00). "
                                    + "Actualiza la pantalla e intenta de nuevo."));
            verify(pagoRepository, never()).save(any());
        }

        @Test
        void unPrestamoSinSaldoYaEstaLiquidado() {
            conCuotasPendientes();

            assertThatThrownBy(() -> servicio.liquidar(request("10", null), LLAVE_PAGO, USUARIO))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.getMessage()).isEqualTo("El préstamo ya está liquidado."));
        }

        @Test
        void sinCuotasVencidasNoHayRecargo() {
            conCuotasPendientes(cuota2);

            assertThatThrownBy(() -> servicio.liquidar(request("1110", "20"), LLAVE_PAGO, USUARIO))
                    .isInstanceOfSatisfying(ApiException.class, error ->
                            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Nested
    class Cancelar {

        private Pago renglon(EstadoPrestamos cuota, int idPago, Instant registro) {
            DatosPago datos = DatosPago.validar(new RegistrarPagoRequest(cuota.getId(), new BigDecimal("1110"), null,
                    HOY, MetodoPago.EFECTIVO, null, null), HOY, "m");
            Pago pago = conId(new OperacionPago(LLAVE_PAGO, "h", datos, USUARIO, registro)
                    .renglon(cuota, datos.importe(), datos.recargo(), TipoPago.LIQUIDACION,
                            new BigDecimal("0.00"), false), "idPago", idPago);
            pago.compartirFolio("P-000240");
            return pago;
        }

        @Test
        void cancelarUnRenglonCancelaLaLiquidacionCompletaYDevuelveElSaldo() {
            cuota1.aplicarAbonos(new BigDecimal("1110"), HOY);
            cuota2.aplicarAbonos(new BigDecimal("1110"), HOY);
            Pago renglon1 = renglon(cuota1, 240, RELOJ.instant());
            Pago renglon2 = renglon(cuota2, 241, RELOJ.instant());
            when(pagoRepository.findFolio(241)).thenReturn(Optional.of("P-000240"));
            when(pagoRepository.bloquearPorFolio("P-000240")).thenReturn(List.of(renglon1, renglon2));
            when(cuotaRepository.bloquear(List.of(101, 102))).thenReturn(List.of(cuota1, cuota2));
            when(pagoRepository.sumarVigentesPorCuota(anyList())).thenReturn(List.of());

            PagoResponse cancelado = servicio.cancelar(241, new CancelarPagoRequest("  Importe capturado mal "), USUARIO);

            assertThat(cancelado.estatus()).isEqualTo(EstatusPago.CANCELADO);
            assertThat(cancelado.motivoCancelacion()).isEqualTo("Importe capturado mal");
            assertThat(cancelado.canceladoPor()).isEqualTo(USUARIO);
            assertThat(renglon1.getEstatus()).isEqualTo(EstatusPago.CANCELADO);
            assertThat(cuota1.estado()).isEqualTo(EstadoCuota.PENDIENTE);
            assertThat(cuota1.getFechaPago()).isNull();
            assertThat(cuota2.estado()).isEqualTo(EstadoCuota.PENDIENTE);
        }

        @Test
        void conOtroAbonoVigenteLaCuotaQuedaEnAbonoParcial() {
            Pago pago = renglon(cuota1, 240, RELOJ.instant());
            when(pagoRepository.findFolio(240)).thenReturn(Optional.of("P-000240"));
            when(pagoRepository.bloquearPorFolio("P-000240")).thenReturn(List.of(pago));
            when(cuotaRepository.bloquear(List.of(101))).thenReturn(List.of(cuota1));
            when(pagoRepository.sumarVigentesPorCuota(List.of(101))).thenReturn(List.of(abono(101, "300")));

            servicio.cancelar(240, new CancelarPagoRequest("Duplicado"), USUARIO);

            assertThat(cuota1.estado()).isEqualTo(EstadoCuota.ABONO_PARCIAL);
        }

        @Test
        void unPagoCanceladoNoSeCancelaDosVeces() {
            Pago pago = renglon(cuota1, 240, RELOJ.instant());
            pago.cancelar("otro", RELOJ.instant(), "Primera vez");
            when(pagoRepository.findFolio(240)).thenReturn(Optional.of("P-000240"));
            when(pagoRepository.bloquearPorFolio("P-000240")).thenReturn(List.of(pago));

            assertThatThrownBy(() -> servicio.cancelar(240, new CancelarPagoRequest("Duplicado"), USUARIO))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.getMessage()).isEqualTo("Este pago ya estaba cancelado."));
        }

        @Test
        void soloSeCancelanPagosDeHoy() {
            // 29 de octubre a las 23:59 en México.
            Pago pago = renglon(cuota1, 240, Instant.parse("2026-10-30T05:59:00Z"));
            when(pagoRepository.findFolio(240)).thenReturn(Optional.of("P-000240"));
            when(pagoRepository.bloquearPorFolio("P-000240")).thenReturn(List.of(pago));

            assertThatThrownBy(() -> servicio.cancelar(240, new CancelarPagoRequest("Duplicado"), USUARIO))
                    .isInstanceOfSatisfying(ApiException.class, error ->
                            assertThat(error.getMessage()).isEqualTo("Solo se pueden cancelar pagos registrados hoy."));
        }

        @Test
        void elMotivoNoPuedeSerSoloEspacios() {
            assertThatThrownBy(() -> servicio.cancelar(240, new CancelarPagoRequest("  ab       "), USUARIO))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(error.getCampos()).singleElement()
                                .satisfies(campo -> assertThat(campo.campo()).isEqualTo("motivo"));
                    });
        }

        @Test
        void pagoInexistenteResponde404() {
            when(pagoRepository.findFolio(9)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.cancelar(9, new CancelarPagoRequest("Duplicado"), USUARIO))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void laCuotaGuardadaComoFloatQuedaEnCeroExacto() {
        EstadoPrestamos cuota = cuota(prestamo, 300, 14, LocalDate.of(2026, 10, 1), "788.57", EstadoCuota.PENDIENTE);

        assertThat(cuota.importe()).isEqualTo(new BigDecimal("788.57"));
        assertThat(cuota.aplicarAbonos(new BigDecimal("788.57"), HOY)).isEqualTo(new BigDecimal("0.00"));
        assertThat(cuota.estado()).isEqualTo(EstadoCuota.PAGADA);
    }
}
