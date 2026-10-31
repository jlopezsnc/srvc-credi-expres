package com.crediexpress.service.impl;

import com.crediexpress.dto.repository.consulta.ResumenMesRepository;
import com.crediexpress.dto.repository.consulta.ResumenMesRepository.Conteo;
import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.dto.view.response.InicioResponse;
import com.crediexpress.dto.view.response.InicioResponse.Hoy;
import com.crediexpress.dto.view.response.InicioResponse.ResumenMes;
import com.crediexpress.dto.view.response.InicioResponse.Semana;
import com.crediexpress.dto.view.response.InicioResponse.Variaciones;
import com.crediexpress.dto.view.response.ReporteCarteraResponse;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.CobranzaConsultaService;
import com.crediexpress.service.InicioService;
import com.crediexpress.service.ReporteService;
import com.crediexpress.util.Dinero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arma el Inicio reutilizando lo que ya existe: el reporte de cobranza (cobrado y aplicado por día,
 * por fecha de registro), el de cartera (saldo, vencido y clientes con atraso) y el resumen de
 * cobranza (próximos 7 días). Solo lo esperado, los otorgados, los liquidados y los convenios salen
 * de {@link ResumenMesRepository}. Sin tablas de cierre: todo se calcula al consultar.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InicioServiceImpl implements InicioService {

    static final int CLIENTES_EN_ATENCION = 5;
    static final int DIAS_PROXIMOS = 7;
    private static final int DIAS_POR_SEMANA = 7;
    private static final int SEMANAS = 5;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ReporteService reporteService;
    private final CobranzaConsultaService cobranzaConsultaService;
    private final ResumenMesRepository resumenMesRepository;
    private final Clock clock;

    @Override
    public InicioResponse resumen(YearMonth mes) {
        LocalDate hoy = LocalDate.now(clock);
        YearMonth actual = YearMonth.from(hoy);
        YearMonth elegido = mes == null ? actual : mes;
        if (elegido.isAfter(actual)) {
            throw ApiException.datosInvalidos("Revisa el mes.",
                    List.of(new CampoError("mes", "Ese mes todavía no empieza.")));
        }
        ResumenMes resumen = resumenDe(elegido, hoy, true);
        ResumenMes anterior = resumenDe(elegido.minusMonths(1), hoy, false);
        Variaciones variaciones = new Variaciones(
                variacion(resumen.cobrado(), anterior.cobrado()),
                variacion(BigDecimal.valueOf(resumen.otorgados()), BigDecimal.valueOf(anterior.otorgados())),
                variacion(BigDecimal.valueOf(resumen.convenios()), BigDecimal.valueOf(anterior.convenios())));
        return new InicioResponse(elegido, elegido.equals(actual), resumen, anterior, variaciones, hoy(hoy));
    }

    private ResumenMes resumenDe(YearMonth mes, LocalDate hoy, boolean conSemanas) {
        LocalDate desde = mes.atDay(1);
        LocalDate fin = mes.atEndOfMonth();
        LocalDate corte = fin.isAfter(hoy) ? hoy : fin;

        ReporteCobranzaResponse cobranza = reporteService.cobranza(desde, corte);
        Map<LocalDate, BigDecimal> esperadoPorDia = resumenMesRepository.esperadoPorDia(desde, fin);
        BigDecimal esperado = suma(esperadoPorDia, desde, fin);
        BigDecimal esperadoALaFecha = suma(esperadoPorDia, desde, corte);
        BigDecimal aplicado = Dinero.oCero(cobranza.totales().importe());

        Conteo otorgados = resumenMesRepository.otorgados(desde, fin);
        Conteo convenios = resumenMesRepository.convenios(inicioUtc(desde), inicioUtc(fin.plusDays(1)));
        List<Semana> semanas = conSemanas ? semanas(desde, fin, esperadoPorDia, cobranza.porDia()) : List.of();
        return new ResumenMes(
                mes,
                desde,
                corte,
                Dinero.oCero(cobranza.totales().totalRecibido()),
                aplicado,
                esperado,
                esperadoALaFecha,
                porcentaje(aplicado, esperadoALaFecha),
                otorgados.cuantos(),
                Dinero.oCero(otorgados.importe()),
                resumenMesRepository.liquidados(desde, fin),
                convenios.cuantos(),
                Dinero.oCero(convenios.importe()),
                semanas);
    }

    private Hoy hoy(LocalDate hoy) {
        ReporteCarteraResponse cartera = reporteService.cartera();
        ReporteCarteraResponse.Totales totales = cartera.totales();
        return new Hoy(
                hoy,
                totales.saldoTotal(),
                totales.saldoVencido(),
                totales.porcentajeVencido(),
                totales.prestamosActivos(),
                totales.clientesConAtraso(),
                cobranzaConsultaService.resumen(hoy, DIAS_PROXIMOS).proximas(),
                cartera.clientesConAtraso().stream().limit(CLIENTES_EN_ATENCION).toList());
    }

    /** Días 1–7, 8–14, 15–21, 22–28 y 29 al fin (en febrero de 28 días no hay quinta). */
    private static List<Semana> semanas(LocalDate desde, LocalDate fin, Map<LocalDate, BigDecimal> esperado,
                                        List<ReporteCobranzaResponse.Dia> cobrado) {
        List<Semana> semanas = new ArrayList<>(SEMANAS);
        for (int i = 0; i < SEMANAS; i++) {
            LocalDate inicio = desde.plusDays((long) i * DIAS_POR_SEMANA);
            if (inicio.isAfter(fin)) {
                break;
            }
            LocalDate termino = i == SEMANAS - 1 ? fin : min(inicio.plusDays(DIAS_POR_SEMANA - 1L), fin);
            BigDecimal aplicado = cobrado.stream()
                    .filter(dia -> !dia.fecha().isBefore(inicio) && !dia.fecha().isAfter(termino))
                    .map(dia -> Dinero.oCero(dia.importe()))
                    .reduce(Dinero.CERO, BigDecimal::add);
            BigDecimal esperadoSemana = suma(esperado, inicio, termino);
            semanas.add(new Semana(i + 1, inicio, termino, esperadoSemana, aplicado,
                    porcentaje(aplicado, esperadoSemana)));
        }
        return semanas;
    }

    private static BigDecimal suma(Map<LocalDate, BigDecimal> porDia, LocalDate desde, LocalDate hasta) {
        return porDia.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(desde) && !e.getKey().isAfter(hasta))
                .map(e -> Dinero.oCero(e.getValue()))
                .reduce(Dinero.CERO, BigDecimal::add);
    }

    /** Parte entre total, en % con un decimal; {@code null} si el total es 0. */
    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? null : parte.multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Cambio contra el mes anterior en %, con un decimal; {@code null} si el anterior es 0. */
    private static BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        return anterior.signum() == 0 ? null
                : actual.subtract(anterior).multiply(CIEN).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private LocalDateTime inicioUtc(LocalDate dia) {
        return LocalDateTime.ofInstant(dia.atStartOfDay(clock.getZone()).toInstant(), ZoneOffset.UTC);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
