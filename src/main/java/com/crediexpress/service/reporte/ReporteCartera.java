package com.crediexpress.service.reporte;

import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.view.response.ReporteCarteraResponse;
import com.crediexpress.dto.view.response.ReporteCarteraResponse.ClienteAtraso;
import com.crediexpress.dto.view.response.ReporteCarteraResponse.Rango;
import com.crediexpress.dto.view.response.ReporteCarteraResponse.Totales;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.CuotaConSaldo;
import com.crediexpress.service.cobranza.SaldosCuotas;
import com.crediexpress.util.Dinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Arma el reporte de cartera a partir de todas las cuotas con saldo. */
public final class ReporteCartera {

    /** Rangos de antigüedad del saldo vencido, en días de atraso. */
    private enum RangoAtraso {
        DE_1_A_7("1 a 7 días", 1, 7),
        DE_8_A_15("8 a 15 días", 8, 15),
        DE_16_A_30("16 a 30 días", 16, 30),
        DE_31_A_60("31 a 60 días", 31, 60),
        MAS_DE_60("Más de 60 días", 61, null);

        private final String etiqueta;
        private final int desde;
        private final Integer hasta;

        RangoAtraso(String etiqueta, int desde, Integer hasta) {
            this.etiqueta = etiqueta;
            this.desde = desde;
            this.hasta = hasta;
        }

        boolean incluye(int dias) {
            return dias >= desde && (hasta == null || dias <= hasta);
        }
    }

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private ReporteCartera() {
    }

    public static ReporteCarteraResponse armar(LocalDate hoy, List<CuotaConSaldo> cuotasConSaldo, CobranzaMapper mapper) {
        // Una cuota con estado pendiente pero ya cubierta por sus abonos no es deuda.
        List<CuotaConSaldo> cuotas = cuotasConSaldo.stream().filter(c -> c.saldo().signum() > 0).toList();
        List<CuotaConSaldo> vencidas = cuotas.stream().filter(c -> c.cuota().vencida(hoy)).toList();

        BigDecimal saldoTotal = SaldosCuotas.total(cuotas);
        BigDecimal saldoVencido = SaldosCuotas.total(vencidas);
        List<ClienteAtraso> clientes = clientesConAtraso(cuotas, vencidas, hoy, mapper);

        Totales totales = new Totales(
                distintos(cuotas, ReporteCartera::idPrestamo),
                distintos(cuotas, ReporteCartera::idCliente),
                clientes.size(),
                vencidas.size(),
                saldoTotal,
                saldoVencido,
                saldoTotal.subtract(saldoVencido),
                porcentaje(saldoVencido, saldoTotal));

        List<Rango> antiguedad = Arrays.stream(RangoAtraso.values())
                .map(rango -> {
                    List<CuotaConSaldo> enRango = vencidas.stream()
                            .filter(c -> rango.incluye(c.cuota().diasAtraso(hoy)))
                            .toList();
                    return new Rango(rango.etiqueta, rango.desde, rango.hasta, enRango.size(),
                            SaldosCuotas.total(enRango));
                })
                .toList();

        return new ReporteCarteraResponse(hoy, totales, antiguedad, clientes);
    }

    /** Del más atrasado al menos; a igual atraso, el que más debe vencido. */
    private static List<ClienteAtraso> clientesConAtraso(List<CuotaConSaldo> cuotas, List<CuotaConSaldo> vencidas,
                                                         LocalDate hoy, CobranzaMapper mapper) {
        Map<Integer, BigDecimal> saldoPorCliente = cuotas.stream().collect(Collectors.groupingBy(
                ReporteCartera::idCliente, Collectors.reducing(Dinero.CERO, CuotaConSaldo::saldo, BigDecimal::add)));
        Map<Integer, List<CuotaConSaldo>> vencidasPorCliente = vencidas.stream()
                .collect(Collectors.groupingBy(ReporteCartera::idCliente, LinkedHashMap::new, Collectors.toList()));

        return vencidasPorCliente.entrySet().stream()
                .map(entrada -> {
                    List<CuotaConSaldo> propias = entrada.getValue();
                    EstadoPrestamos masAntigua = propias.stream()
                            .map(CuotaConSaldo::cuota)
                            .min(Comparator.comparing(EstadoPrestamos::getFechaVencimiento))
                            .orElseThrow();
                    return new ClienteAtraso(
                            mapper.cliente(masAntigua.getIdCliente()),
                            distintos(propias, ReporteCartera::idPrestamo),
                            propias.size(),
                            SaldosCuotas.total(propias),
                            saldoPorCliente.getOrDefault(entrada.getKey(), Dinero.CERO),
                            masAntigua.diasAtraso(hoy),
                            masAntigua.getFechaVencimiento());
                })
                .sorted(Comparator.comparingInt(ClienteAtraso::diasMaximoAtraso).reversed()
                        .thenComparing(ClienteAtraso::saldoVencido, Comparator.reverseOrder()))
                .toList();
    }

    private static int distintos(List<CuotaConSaldo> cuotas, Function<CuotaConSaldo, Integer> clave) {
        return (int) cuotas.stream().map(clave).distinct().count();
    }

    private static Integer idCliente(CuotaConSaldo cuota) {
        return cuota.cuota().getIdCliente().getIdCliente();
    }

    private static Integer idPrestamo(CuotaConSaldo cuota) {
        return cuota.cuota().getIdCalculadoraPrestamo().getIdCalculadoraPrestamo();
    }

    /** Con un decimal: 1945 de 15560 → 12.5. */
    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
    }
}
