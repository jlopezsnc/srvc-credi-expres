package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.Convenio;
import com.crediexpress.dto.entity.ConvenioPrestamo;
import com.crediexpress.dto.entity.EstatusConvenio;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.entity.SituacionConvenio;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.repository.ConvenioRepository;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.repository.consulta.ConvenioConsultaRepository;
import com.crediexpress.dto.repository.consulta.ConvenioConsultaRepository.Consulta;
import com.crediexpress.dto.repository.consulta.ConvenioConsultaRepository.Fila;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroConvenios;
import com.crediexpress.dto.view.request.OrdenConvenios;
import com.crediexpress.dto.view.response.ConvenioDetalleResponse;
import com.crediexpress.dto.view.response.PaginaConveniosResponse;
import com.crediexpress.dto.view.response.PrestamoDetalleResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.PrestamoConsultaService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvenioConsultaServiceImplTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-31T05:30:00Z"),
            ZoneId.of("America/Mexico_City"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    @Mock
    private ConvenioConsultaRepository consultaRepository;
    @Mock
    private ConvenioRepository convenioRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private PrestamoConsultaService prestamoConsultaService;

    private ConvenioConsultaServiceImpl servicio;

    private final Cliente maria = Cliente.builder().idCliente(1).nombreCompleto("MARÍA DE PRUEBA").build();
    private final CalculadoraPrestamos nuevo = CalculadoraPrestamos.builder().idCalculadoraPrestamo(46).build();
    private final CalculadoraPrestamos origen = CalculadoraPrestamos.builder().idCalculadoraPrestamo(12)
            .numeroCuotas(12).build();

    @BeforeEach
    void preparar() {
        servicio = new ConvenioConsultaServiceImpl(consultaRepository, convenioRepository, pagoRepository,
                prestamoConsultaService, RELOJ);
    }

    private static Fila fila(SituacionConvenio situacion) {
        return new Fila(124, "CV-000124", 1, "MARÍA DE PRUEBA", null, 46, 1, "Semanal", 12, 1, 1, 0, 0,
                new BigDecimal("4760.0000"), new BigDecimal("4760.0000"), new BigDecimal("396.6700"),
                new BigDecimal("4760.0000"), situacion, EstatusConvenio.VIGENTE, Instant.parse("2026-10-30T18:00:00Z"));
    }

    private Convenio convenio() {
        Convenio convenio = Convenio.builder().folio("CV-000124").titular(maria).prestamoConvenio(nuevo)
                .modalidad(Modalidad.builder().idModalidad(1).descripcion("Semanal").diasActivos(7).build())
                .numeroPagos(12).tasaInteres(BigDecimal.ZERO).interes(BigDecimal.ZERO).motivo("Paga ella.")
                .registradoPor("cobranza1@ejemplo.mx").ipOrigen("10.0.0.7").build();
        convenio.agregar(ConvenioPrestamo.builder().prestamo(origen).cliente(maria)
                .situacionAnterior(SituacionPrestamo.CON_ATRASO).cuotasConvenidas(7).cuotasVencidas(3)
                .saldoConvenido(new BigDecimal("4760.00")).build());
        return convenio;
    }

    private void conDetalle(long pagos) {
        when(consultaRepository.buscarPorId(124, HOY)).thenReturn(Optional.of(fila(SituacionConvenio.AL_CORRIENTE)));
        when(convenioRepository.buscarDetalle(124)).thenReturn(Optional.of(convenio()));
        when(prestamoConsultaService.detalle(46)).thenReturn(new PrestamoDetalleResponse(null, List.of()));
        when(pagoRepository.contarPagosVigentesDelPrestamo(46)).thenReturn(pagos);
    }

    @Test
    void laListaUsaHoyDeMexicoYSumaLosContadores() {
        Consulta consulta = new Consulta(FiltroConvenios.VIGENTES, null, OrdenConvenios.FECHA, Direccion.DESC, 0, 10,
                HOY);
        when(consultaRepository.buscar(consulta)).thenReturn(List.of(fila(SituacionConvenio.AL_CORRIENTE)));
        when(consultaRepository.contar(consulta)).thenReturn(1L);
        when(consultaRepository.contarPorSituacion(HOY)).thenReturn(Map.of(SituacionConvenio.AL_CORRIENTE, 4L,
                SituacionConvenio.CON_ATRASO, 2L, SituacionConvenio.LIQUIDADO, 1L, SituacionConvenio.CANCELADO, 3L));

        PaginaConveniosResponse pagina = servicio.listar(FiltroConvenios.VIGENTES, null, OrdenConvenios.FECHA,
                Direccion.DESC, 0, 10);

        assertThat(pagina.contadores()).isEqualTo(new PaginaConveniosResponse.Contadores(6, 2, 1, 3, 10));
        assertThat(pagina.contenido().getFirst().saldoConvenido()).isEqualTo(new BigDecimal("4760.00"));
        assertThat(pagina.contenido().getFirst().titular().nombreCompleto()).isEqualTo("MARÍA DE PRUEBA");
    }

    @Test
    void sinPagosSePuedeCancelarYTraeLaBitacora() {
        conDetalle(0);

        ConvenioDetalleResponse detalle = servicio.detalle(124);

        assertThat(detalle.puedeCancelar()).isTrue();
        assertThat(detalle.motivoNoCancelable()).isNull();
        assertThat(detalle.meses()).isEqualTo(new BigDecimal("3"));
        assertThat(detalle.tasaInteres()).isEqualTo(new BigDecimal("0.00"));
        assertThat(detalle.bitacora().registradoPor()).isEqualTo("cobranza1@ejemplo.mx");
        assertThat(detalle.bitacora().ipOrigen()).isEqualTo("10.0.0.7");
        assertThat(detalle.prestamos()).singleElement().satisfies(p -> {
            assertThat(p.estado()).isEqualTo(SituacionPrestamo.CON_ATRASO);
            assertThat(p.cuotasPagadas()).isEqualTo(5);
        });
    }

    @Test
    void conPagosNoSePuedeCancelarYDiceporQue() {
        conDetalle(1);

        ConvenioDetalleResponse detalle = servicio.detalle(124);

        assertThat(detalle.puedeCancelar()).isFalse();
        assertThat(detalle.pagosRegistrados()).isEqualTo(1);
        assertThat(detalle.motivoNoCancelable()).contains("ya tiene 1 pago registrado");
    }

    @Test
    void unConvenioQueNoExisteResponde404() {
        when(consultaRepository.buscarPorId(99, HOY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalle(99)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
