package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.Convenio;
import com.crediexpress.dto.entity.ConvenioPrestamo;
import com.crediexpress.dto.entity.EstatusConvenio;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.repository.ConvenioRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.ModalidadRepository;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Prestamo;
import com.crediexpress.dto.view.request.CancelarConvenioRequest;
import com.crediexpress.dto.view.request.GenerarConvenioRequest;
import com.crediexpress.dto.view.request.SimularConvenioRequest;
import com.crediexpress.dto.view.response.ConvenioResponse;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.CalculadoraPrestamo.Plan;
import com.crediexpress.service.ConvenioConsultaService;
import com.crediexpress.service.cobranza.CuotaConSaldo;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.ResultadoOperacion;
import com.crediexpress.service.cobranza.SaldosCuotas;
import com.crediexpress.service.convenio.HuellaConvenio;
import com.crediexpress.service.prestamo.RegistroPrestamo;
import com.crediexpress.service.prestamo.RegistroPrestamo.Registrado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConvenioServiceImplTest {

    private static final ZoneId MEXICO = ZoneId.of("America/Mexico_City");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), MEXICO);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);
    private static final LlaveIdempotencia LLAVE = new LlaveIdempotencia("0b7c2f8e-3d4a-4c1b-9e5f-6a7b8c9d0e1f");
    private static final String USUARIO = "cobrador@ejemplo.mx";
    private static final String IP = "10.0.0.7";

    @Mock
    private PrestamoConsultaRepository consultaRepository;
    @Mock
    private EstadoPrestamosRepository cuotaRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ModalidadRepository modalidadRepository;
    @Mock
    private ConvenioRepository convenioRepository;
    @Mock
    private SaldosCuotas saldosCuotas;
    @Mock
    private RegistroPrestamo registroPrestamo;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ConvenioConsultaService consultaService;
    @Captor
    private ArgumentCaptor<Convenio> convenioGuardado;
    @Captor
    private ArgumentCaptor<Plan> planRegistrado;

    private ConvenioServiceImpl servicio;

    private final Cliente maria = Cliente.builder().idCliente(1).nombreCompleto("MARÍA DE PRUEBA").build();
    private final Cliente ana = Cliente.builder().idCliente(2).nombreCompleto("ANA DE PRUEBA").build();
    private final Modalidad semanal = Modalidad.builder().idModalidad(1).descripcion("Semanal").diasActivos(7).build();
    private final CalculadoraPrestamos prestamo12 = CalculadoraPrestamos.builder().idCalculadoraPrestamo(12)
            .idCliente(maria).numeroCuotas(12).build();
    private final CalculadoraPrestamos prestamo18 = CalculadoraPrestamos.builder().idCalculadoraPrestamo(18)
            .idCliente(ana).numeroCuotas(8).build();

    @BeforeEach
    void preparar() {
        servicio = new ConvenioServiceImpl(consultaRepository, cuotaRepository, clienteRepository, modalidadRepository,
                convenioRepository, saldosCuotas, registroPrestamo, pagoRepository, consultaService, RELOJ);
        when(modalidadRepository.findById(1)).thenReturn(Optional.of(semanal));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(maria));
        when(consultaRepository.buscarPorId(12, HOY)).thenReturn(Optional.of(
                prestamo(12, 1, "MARÍA DE PRUEBA", 12, "4760.00", SituacionPrestamo.CON_ATRASO)));
        when(consultaRepository.buscarPorId(18, HOY)).thenReturn(Optional.of(
                prestamo(18, 2, "ANA DE PRUEBA", 8, "5150.00", SituacionPrestamo.AL_CORRIENTE)));
    }

    private static Prestamo prestamo(int id, int idCliente, String cliente, int cuotas, String saldo,
                                     SituacionPrestamo situacion) {
        return new Prestamo(id, idCliente, cliente, null, 1, "Semanal", cuotas, new BigDecimal("100.00"), 2, 1,
                new BigDecimal("9999.00"), new BigDecimal(saldo), HOY, 3, situacion,
                situacion == SituacionPrestamo.EN_CONVENIO ? "CV-000009" : null, null);
    }

    private static SimularConvenioRequest simulacion(List<Integer> ids, String tasa, LocalDate primerPago) {
        return new SimularConvenioRequest(ids, 1, 12, new BigDecimal(tasa), primerPago);
    }

    private static GenerarConvenioRequest generacion(String saldoEsperado) {
        return new GenerarConvenioRequest(1, List.of(18, 12), 1, 12, BigDecimal.ZERO, HOY.plusDays(7),
                "  Los préstamos de su hermana los paga ella.  ", new BigDecimal(saldoEsperado));
    }

    private static EstadoPrestamos cuota(int id, CalculadoraPrestamos prestamo, LocalDate vence, int estado) {
        return EstadoPrestamos.builder().id(id).idCalculadoraPrestamo(prestamo).idCliente(prestamo.getIdCliente())
                .numeroCuota(id % 10).fechaVencimiento(vence).importeCuota(new BigDecimal("2000.00"))
                .estadoPago(estado).build();
    }

    @Nested
    class Simular {

        @Test
        void sumaLosSaldosYArmaElPlanSinInteres() {
            SimulacionConvenioResponse plan = servicio.simular(simulacion(List.of(12, 18, 12), "0", HOY.plusDays(7)));

            assertThat(plan.prestamos()).extracting(SimulacionConvenioResponse.PrestamoConvenido::idPrestamo)
                    .containsExactly(12, 18);
            assertThat(plan.personas()).isEqualTo(2);
            assertThat(plan.saldoConvenido()).isEqualTo(new BigDecimal("9910.00"));
            assertThat(plan.interes()).isEqualTo(new BigDecimal("0.00"));
            assertThat(plan.totalPagar()).isEqualTo(new BigDecimal("9910.00"));
            assertThat(plan.meses()).isEqualTo(new BigDecimal("3"));
            // 9910 / 12 = 825.83; el último pago absorbe los centavos.
            assertThat(plan.importeCuota()).isEqualTo(new BigDecimal("825.83"));
            assertThat(plan.importeUltimaCuota()).isEqualTo(new BigDecimal("825.87"));
            assertThat(plan.calendario()).hasSize(12);
            assertThat(plan.calendario().get(1).fechaVencimiento()).isEqualTo(HOY.plusDays(14));
        }

        @Test
        void conInteresExcepcionalLoCobraPorMes() {
            SimulacionConvenioResponse plan = servicio.simular(simulacion(List.of(12), "5", HOY.plusDays(7)));

            // 4760 × 5% × 3 meses.
            assertThat(plan.interes()).isEqualTo(new BigDecimal("714.00"));
            assertThat(plan.totalPagar()).isEqualTo(new BigDecimal("5474.00"));
            assertThat(plan.tasaInteres()).isEqualTo(new BigDecimal("5.00"));
        }

        @Test
        void rechazaPrestamosLiquidadosOYaEnConvenio() {
            when(consultaRepository.buscarPorId(30, HOY)).thenReturn(Optional.of(
                    prestamo(30, 1, "X", 4, "0", SituacionPrestamo.EN_CONVENIO)));
            when(consultaRepository.buscarPorId(31, HOY)).thenReturn(Optional.of(
                    prestamo(31, 1, "X", 4, "0", SituacionPrestamo.LIQUIDADO)));

            assertThatThrownBy(() -> servicio.simular(simulacion(List.of(12, 30), "0", HOY)))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getMessage()).contains("#30").contains("CV-000009");
                    });
            assertThatThrownBy(() -> servicio.simular(simulacion(List.of(31), "0", HOY)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getMessage()).isEqualTo("El préstamo #31 ya está liquidado."));
        }

        @Test
        void validaElPrestamoLaPeriodicidadYLaFecha() {
            when(consultaRepository.buscarPorId(99, HOY)).thenReturn(Optional.empty());
            when(modalidadRepository.findById(3)).thenReturn(Optional.of(
                    Modalidad.builder().idModalidad(3).descripcion("Mensual").diasActivos(30).build()));

            assertThatThrownBy(() -> servicio.simular(simulacion(List.of(99), "0", HOY)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
            assertThatThrownBy(() -> servicio.simular(new SimularConvenioRequest(List.of(12), 3, 4, BigDecimal.ZERO,
                    HOY))).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> servicio.simular(simulacion(List.of(12), "0", HOY.minusDays(1))))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Nested
    class Cancelar {

        private final CalculadoraPrestamos prestamoNuevo = CalculadoraPrestamos.builder().idCalculadoraPrestamo(46)
                .numeroCuotas(2).build();
        private EstadoPrestamos n1;
        private EstadoPrestamos n2;
        private EstadoPrestamos c121;
        private EstadoPrestamos c181;
        private Convenio convenio;

        @BeforeEach
        void convenioVigenteSinPagos() {
            n1 = cuota(461, prestamoNuevo, HOY.plusDays(7), EstadoCuota.PENDIENTE.codigo());
            n2 = cuota(462, prestamoNuevo, HOY.plusDays(14), EstadoCuota.PENDIENTE.codigo());
            c121 = cuota(121, prestamo12, HOY.minusDays(10), EstadoCuota.EN_CONVENIO.codigo());
            c181 = cuota(181, prestamo18, HOY.plusDays(6), EstadoCuota.EN_CONVENIO.codigo());
            convenio = Convenio.builder().folio("CV-000124").titular(maria).prestamoConvenio(prestamoNuevo)
                    .modalidad(semanal).registradoPor(USUARIO).build();
            // Se agregan en desorden: los bloqueos deben ir en orden de id.
            convenio.agregar(ConvenioPrestamo.builder().prestamo(prestamo18).cliente(ana).build());
            convenio.agregar(ConvenioPrestamo.builder().prestamo(prestamo12).cliente(maria).build());
            when(convenioRepository.bloquear(124)).thenReturn(Optional.of(convenio));
            when(cuotaRepository.bloquearDelPrestamo(46)).thenReturn(List.of(n1, n2));
            when(pagoRepository.contarPagosVigentesDelPrestamo(46)).thenReturn(0L);
            when(cuotaRepository.bloquearDelPrestamoConEstado(12, EstadoCuota.EN_CONVENIO.codigo()))
                    .thenReturn(List.of(c121));
            when(cuotaRepository.bloquearDelPrestamoConEstado(18, EstadoCuota.EN_CONVENIO.codigo()))
                    .thenReturn(List.of(c181));
            when(saldosCuotas.calcular(List.of(c121))).thenReturn(
                    List.of(new CuotaConSaldo(c121, new BigDecimal("760.00"), new BigDecimal("0.00"))));
            when(saldosCuotas.calcular(List.of(c181))).thenReturn(
                    List.of(new CuotaConSaldo(c181, new BigDecimal("0.00"), new BigDecimal("0.00"))));
        }

        @Test
        void cancelaElPrestamoNuevoRegresaLasCuotasYGuardaLaBitacora() {
            servicio.cancelar(124, new CancelarConvenioRequest("  La clienta no aceptó las condiciones.  "), USUARIO);

            assertThat(List.of(n1, n2)).allSatisfy(
                    cuota -> assertThat(cuota.getEstadoPago()).isEqualTo(EstadoCuota.CANCELADA.codigo()));
            // Con abonos queda en abono parcial; sin abonos, pendiente.
            assertThat(c121.getEstadoPago()).isEqualTo(EstadoCuota.ABONO_PARCIAL.codigo());
            assertThat(c181.getEstadoPago()).isEqualTo(EstadoCuota.PENDIENTE.codigo());
            assertThat(convenio.getEstatus()).isEqualTo(EstatusConvenio.CANCELADO);
            assertThat(convenio.getCanceladoPor()).isEqualTo(USUARIO);
            assertThat(convenio.getActualizadoPor()).isEqualTo(USUARIO);
            assertThat(convenio.getFechaCancelacion()).isEqualTo(RELOJ.instant());
            assertThat(convenio.getMotivoCancelacion()).isEqualTo("La clienta no aceptó las condiciones.");
            assertThat(convenio.getPrestamos()).allSatisfy(
                    origen -> assertThat(origen.getEstatus()).isEqualTo(EstatusConvenio.CANCELADO));

            InOrder orden = inOrder(convenioRepository, cuotaRepository, consultaService);
            orden.verify(convenioRepository).bloquear(124);
            orden.verify(cuotaRepository).bloquearDelPrestamo(46);
            orden.verify(cuotaRepository).bloquearDelPrestamoConEstado(12, EstadoCuota.EN_CONVENIO.codigo());
            orden.verify(cuotaRepository).bloquearDelPrestamoConEstado(18, EstadoCuota.EN_CONVENIO.codigo());
            orden.verify(convenioRepository).flush();
            orden.verify(consultaService).detalle(124);
        }

        @Test
        void conPagosNoSeCancelaNiSeTocaNada() {
            when(pagoRepository.contarPagosVigentesDelPrestamo(46)).thenReturn(2L);

            assertThatThrownBy(() -> servicio.cancelar(124, new CancelarConvenioRequest("Se capturó por error."),
                    USUARIO)).isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getMessage()).contains("ya tiene 2 pagos registrados");
                    });
            assertThat(n1.getEstadoPago()).isEqualTo(EstadoCuota.PENDIENTE.codigo());
            assertThat(c121.getEstadoPago()).isEqualTo(EstadoCuota.EN_CONVENIO.codigo());
            assertThat(convenio.getEstatus()).isEqualTo(EstatusConvenio.VIGENTE);
        }

        @Test
        void unConvenioYaCanceladoResponde409() {
            convenio.cancelar("otro@ejemplo.mx", RELOJ.instant(), "Primera cancelación.");

            assertThatThrownBy(() -> servicio.cancelar(124, new CancelarConvenioRequest("Se capturó por error."),
                    USUARIO)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getMessage()).isEqualTo("Este convenio ya estaba cancelado."));
        }

        @Test
        void validaElMotivoYQueExista() {
            assertThatThrownBy(() -> servicio.cancelar(124, new CancelarConvenioRequest("   corto    "), USUARIO))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            when(convenioRepository.bloquear(99)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.cancelar(99, new CancelarConvenioRequest("Se capturó por error."),
                    USUARIO)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
            verifyNoInteractions(consultaService);
        }
    }

    @Nested
    class Generar {

        private EstadoPrestamos c121;
        private EstadoPrestamos c122;
        private EstadoPrestamos c181;
        private final CalculadoraPrestamos prestamoNuevo = CalculadoraPrestamos.builder().idCalculadoraPrestamo(46)
                .numeroCuotas(12).build();

        @BeforeEach
        void conCuotasConSaldo() {
            c121 = cuota(121, prestamo12, HOY.minusDays(10), EstadoCuota.ABONO_PARCIAL.codigo());
            c122 = cuota(122, prestamo12, HOY.plusDays(4), EstadoCuota.PENDIENTE.codigo());
            c181 = cuota(181, prestamo18, HOY.plusDays(6), EstadoCuota.PENDIENTE.codigo());
            List<EstadoPrestamos> de12 = List.of(c121, c122);
            List<EstadoPrestamos> de18 = List.of(c181);
            when(cuotaRepository.bloquearConSaldoDelPrestamo(12)).thenReturn(de12);
            when(cuotaRepository.bloquearConSaldoDelPrestamo(18)).thenReturn(de18);
            // 12: 1240 + 2000 = 3240 (la primera con 760 abonados); 18: 2000. Total 5240.
            when(saldosCuotas.calcular(de12)).thenReturn(List.of(
                    new CuotaConSaldo(c121, new BigDecimal("760.00"), new BigDecimal("1240.00")),
                    new CuotaConSaldo(c122, new BigDecimal("0.00"), new BigDecimal("2000.00"))));
            when(saldosCuotas.calcular(de18)).thenReturn(List.of(
                    new CuotaConSaldo(c181, new BigDecimal("0.00"), new BigDecimal("2000.00"))));
            when(convenioRepository.findByLlaveIdempotencia(LLAVE.valor())).thenReturn(Optional.empty());
            when(registroPrestamo.registrar(eq(maria), eq(semanal), any(), eq(HOY)))
                    .thenReturn(new Registrado(prestamoNuevo, List.of()));
            when(convenioRepository.save(any())).thenAnswer(invocacion -> {
                Convenio convenio = invocacion.getArgument(0);
                ReflectionTestUtils.setField(convenio, "idConvenio", 124);
                return convenio;
            });
        }

        @Test
        void bloqueaEnOrdenConvieneLasCuotasYGuardaLaBitacora() {
            ResultadoOperacion<ConvenioResponse> resultado = servicio.generar(generacion("5240.00"), LLAVE, USUARIO, IP);

            InOrder orden = inOrder(cuotaRepository);
            orden.verify(cuotaRepository).bloquearConSaldoDelPrestamo(12);
            orden.verify(cuotaRepository).bloquearConSaldoDelPrestamo(18);

            verify(registroPrestamo).registrar(eq(maria), eq(semanal), planRegistrado.capture(), eq(HOY));
            assertThat(planRegistrado.getValue().monto()).isEqualTo(new BigDecimal("5240.00"));
            assertThat(planRegistrado.getValue().interes()).isEqualTo(new BigDecimal("0.00"));
            assertThat(List.of(c121, c122, c181)).allSatisfy(
                    cuota -> assertThat(cuota.getEstadoPago()).isEqualTo(EstadoCuota.EN_CONVENIO.codigo()));

            verify(convenioRepository).save(convenioGuardado.capture());
            Convenio convenio = convenioGuardado.getValue();
            assertThat(convenio.getFolio()).isEqualTo("CV-000124");
            assertThat(convenio.getPrestamoConvenio()).isSameAs(prestamoNuevo);
            assertThat(convenio.getMotivo()).isEqualTo("Los préstamos de su hermana los paga ella.");
            assertThat(convenio.getRegistradoPor()).isEqualTo(USUARIO);
            assertThat(convenio.getIpOrigen()).isEqualTo(IP);
            assertThat(convenio.getFechaRegistro()).isEqualTo(RELOJ.instant());
            assertThat(convenio.getHashPeticion()).isEqualTo(HuellaConvenio.de(generacion("5240.00")));
            assertThat(convenio.getPrestamos()).hasSize(2).first().satisfies(origen -> {
                assertThat(origen.getPrestamo()).isSameAs(prestamo12);
                assertThat(origen.getCliente()).isSameAs(maria);
                assertThat(origen.getSituacionAnterior()).isEqualTo(SituacionPrestamo.CON_ATRASO);
                assertThat(origen.getCuotasConvenidas()).isEqualTo(2);
                assertThat(origen.getCuotasVencidas()).isEqualTo(1);
                assertThat(origen.getAbonadoConvenido()).isEqualTo(new BigDecimal("760.00"));
                assertThat(origen.getSaldoConvenido()).isEqualTo(new BigDecimal("3240.00"));
                assertThat(origen.getRegistradoPor()).isEqualTo(USUARIO);
            });
            assertThat(convenio.getPrestamos().get(1).getCliente()).isSameAs(ana);

            assertThat(resultado.repetida()).isFalse();
            assertThat(resultado.valor().folio()).isEqualTo("CV-000124");
            assertThat(resultado.valor().idPrestamoConvenio()).isEqualTo(46);
            assertThat(resultado.valor().plan().personas()).isEqualTo(2);
        }

        @Test
        void siElSaldoCambioNoConvieneNada() {
            assertThatThrownBy(() -> servicio.generar(generacion("9910.00"), LLAVE, USUARIO, IP))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getMessage()).contains("cambió");
                    });

            verify(registroPrestamo, never()).registrar(any(), any(), any(), any());
            verify(convenioRepository, never()).save(any());
            assertThat(c121.getEstadoPago()).isEqualTo(EstadoCuota.ABONO_PARCIAL.codigo());
        }

        @Test
        void conLaMismaLlaveDevuelveElConvenioYaRegistrado() {
            Convenio previo = Convenio.builder().llaveIdempotencia(LLAVE.valor())
                    .hashPeticion(HuellaConvenio.de(generacion("5240.00"))).titular(maria)
                    .prestamoConvenio(prestamoNuevo).modalidad(semanal).numeroPagos(12)
                    .saldoConvenido(new BigDecimal("5240.00")).tasaInteres(BigDecimal.ZERO)
                    .interes(BigDecimal.ZERO).totalPagar(new BigDecimal("5240.00"))
                    .importeCuota(new BigDecimal("436.67")).motivo("x").registradoPor(USUARIO).build();
            when(convenioRepository.findByLlaveIdempotencia(LLAVE.valor())).thenReturn(Optional.of(previo));
            when(cuotaRepository.cuotasDelPrestamo(46)).thenReturn(List.of());

            ResultadoOperacion<ConvenioResponse> resultado = servicio.generar(generacion("5240.00"), LLAVE, USUARIO, IP);

            assertThat(resultado.repetida()).isTrue();
            verify(registroPrestamo, never()).registrar(any(), any(), any(), any());
        }

        @Test
        void unaLlaveReutilizadaConOtrosDatosResponde409() {
            Convenio previo = Convenio.builder().llaveIdempotencia(LLAVE.valor()).hashPeticion("otra").build();
            when(convenioRepository.findByLlaveIdempotencia(LLAVE.valor())).thenReturn(Optional.of(previo));

            assertThatThrownBy(() -> servicio.generar(generacion("5240.00"), LLAVE, USUARIO, IP))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getMessage()).isEqualTo(ConvenioServiceImpl.LLAVE_REUTILIZADA));
        }

        @Test
        void unTitularQueNoExisteResponde404() {
            when(clienteRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.generar(generacion("5240.00"), LLAVE, USUARIO, IP))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
