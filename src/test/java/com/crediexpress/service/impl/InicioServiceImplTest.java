package com.crediexpress.service.impl;

import com.crediexpress.dto.repository.consulta.ResumenMesRepository;
import com.crediexpress.dto.repository.consulta.ResumenMesRepository.Conteo;
import com.crediexpress.dto.view.response.ClienteResumenResponse;
import com.crediexpress.dto.view.response.InicioResponse;
import com.crediexpress.dto.view.response.InicioResponse.Semana;
import com.crediexpress.dto.view.response.ReporteCarteraResponse;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse;
import com.crediexpress.dto.view.response.ResumenCobranzaResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.CobranzaConsultaService;
import com.crediexpress.service.ReporteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InicioServiceImplTest {

    // 30 de octubre de 2026 a las 23:30 en México (ya es 31 en UTC): "hoy" es el 30.
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-31T05:30:00Z"),
            ZoneId.of("America/Mexico_City"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    @Mock
    private ReporteService reporteService;
    @Mock
    private CobranzaConsultaService cobranzaConsultaService;
    @Mock
    private ResumenMesRepository resumenMesRepository;

    private InicioServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new InicioServiceImpl(reporteService, cobranzaConsultaService, resumenMesRepository, RELOJ);
        // Octubre (en curso, del 1 al 30): 1,000 aplicados por día; septiembre: 500 por día.
        when(reporteService.cobranza(LocalDate.of(2026, 10, 1), HOY)).thenReturn(cobranza(
                LocalDate.of(2026, 10, 1), HOY, "1000.00", "30000.00", "30500.00"));
        when(reporteService.cobranza(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(cobranza(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "500.00", "15000.00", "15000.00"));
        when(resumenMesRepository.esperadoPorDia(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(Map.of(LocalDate.of(2026, 10, 2), new BigDecimal("20000.00"),
                        LocalDate.of(2026, 10, 30), new BigDecimal("12000.00"),
                        LocalDate.of(2026, 10, 31), new BigDecimal("8000.00")));
        when(resumenMesRepository.esperadoPorDia(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(Map.of(LocalDate.of(2026, 9, 15), new BigDecimal("16000.00")));
        when(resumenMesRepository.otorgados(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(new Conteo(11, new BigDecimal("79000.00")));
        when(resumenMesRepository.otorgados(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new Conteo(10, new BigDecimal("50000.00")));
        when(resumenMesRepository.convenios(any(), any())).thenReturn(new Conteo(0, BigDecimal.ZERO));
        when(reporteService.cartera()).thenReturn(cartera());
        when(cobranzaConsultaService.resumen(HOY, 7)).thenReturn(new ResumenCobranzaResponse(HOY, null, null,
                new ResumenCobranzaResponse.Cuotas(23, new BigDecimal("16240.00")), null));
    }

    private static ReporteCobranzaResponse cobranza(LocalDate desde, LocalDate hasta, String porDia, String importe,
                                                    String totalRecibido) {
        List<ReporteCobranzaResponse.Dia> dias = IntStream.rangeClosed(0, (int) (hasta.toEpochDay() - desde.toEpochDay()))
                .mapToObj(i -> new ReporteCobranzaResponse.Dia(desde.plusDays(i), 1, new BigDecimal(porDia),
                        BigDecimal.ZERO, new BigDecimal(porDia)))
                .toList();
        return new ReporteCobranzaResponse(desde, hasta, new ReporteCobranzaResponse.Totales(30,
                new BigDecimal(importe), BigDecimal.ZERO, new BigDecimal(totalRecibido)), null, dias, List.of(), List.of());
    }

    private static ReporteCarteraResponse cartera() {
        List<ReporteCarteraResponse.ClienteAtraso> atrasados = IntStream.rangeClosed(1, 7)
                .mapToObj(i -> new ReporteCarteraResponse.ClienteAtraso(
                        new ClienteResumenResponse(i, "CLIENTE " + i, null), 1, 1, BigDecimal.TEN, BigDecimal.TEN,
                        40 - i, HOY.minusDays(40 - i)))
                .toList();
        return new ReporteCarteraResponse(HOY, new ReporteCarteraResponse.Totales(38, 30, 7, 12,
                new BigDecimal("427800.00"), new BigDecimal("38700.00"), new BigDecimal("389100.00"),
                new BigDecimal("9.0")), List.of(), atrasados);
    }

    @Test
    void elMesEnCursoCompararaContraLoEsperadoALaFechaYArmaLasSemanas() {
        InicioResponse inicio = servicio.resumen(null);

        assertThat(inicio.mes()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(inicio.enCurso()).isTrue();
        assertThat(inicio.resumen().corte()).isEqualTo(HOY);
        assertThat(inicio.resumen().cobrado()).isEqualByComparingTo("30500.00");
        assertThat(inicio.resumen().esperado()).isEqualByComparingTo("40000.00");
        // Hasta el 30: 20,000 + 12,000 (lo del 31 todavía no vence).
        assertThat(inicio.resumen().esperadoALaFecha()).isEqualByComparingTo("32000.00");
        assertThat(inicio.resumen().cumplimiento()).isEqualByComparingTo("93.8");

        List<Semana> semanas = inicio.resumen().semanas();
        assertThat(semanas).extracting(Semana::numero).containsExactly(1, 2, 3, 4, 5);
        assertThat(semanas.getFirst().esperado()).isEqualByComparingTo("20000.00");
        assertThat(semanas.getFirst().aplicado()).isEqualByComparingTo("7000.00");
        assertThat(semanas.getFirst().cumplimiento()).isEqualByComparingTo("35.0");
        assertThat(semanas.get(1).cumplimiento()).isNull();
        assertThat(semanas.getLast().desde()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(semanas.getLast().hasta()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(semanas.getLast().esperado()).isEqualByComparingTo("20000.00");
        // Del 29 al 30: lo del 31 todavía no se cobra.
        assertThat(semanas.getLast().aplicado()).isEqualByComparingTo("2000.00");
        assertThat(inicio.anterior().semanas()).isEmpty();
    }

    @Test
    void comparaContraElMesAnteriorYSinBaseNoInventaVariacion() {
        InicioResponse inicio = servicio.resumen(YearMonth.of(2026, 10));

        assertThat(inicio.variaciones().cobrado()).isEqualByComparingTo("103.3");
        assertThat(inicio.variaciones().otorgados()).isEqualByComparingTo("10.0");
        assertThat(inicio.variaciones().convenios()).isNull();
        assertThat(inicio.anterior().cumplimiento()).isEqualByComparingTo("93.8");
    }

    @Test
    void alDiaDeHoyReutilizaCarteraYCobranza() {
        InicioResponse.Hoy hoy = servicio.resumen(null).hoy();

        assertThat(hoy.cartera()).isEqualByComparingTo("427800.00");
        assertThat(hoy.porcentajeVencido()).isEqualByComparingTo("9.0");
        assertThat(hoy.proximos7Dias().cuotas()).isEqualTo(23);
        assertThat(hoy.requierenAtencion()).hasSize(5).first()
                .satisfies(c -> assertThat(c.cliente().nombreCompleto()).isEqualTo("CLIENTE 1"));
    }

    @Test
    void losConveniosSeBuscanConElMesEnHoraDeMexicoPasadoAUtc() {
        servicio.resumen(null);

        // 1 de octubre 00:00 en México = 06:00 UTC.
        verify(resumenMesRepository).convenios(eq(LocalDateTime.of(2026, 10, 1, 6, 0)),
                eq(LocalDateTime.of(2026, 11, 1, 6, 0)));
    }

    @Test
    void unMesQueNoHaEmpezadoResponde400() {
        assertThatThrownBy(() -> servicio.resumen(YearMonth.of(2026, 11)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
