package com.crediexpress.service.cobranza;

import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.repository.PagoRepository.AbonoCuota;
import com.crediexpress.util.Dinero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Saldo de varias cuotas con una sola consulta a {@code pago} (sin N+1). */
@Component
@RequiredArgsConstructor
public class SaldosCuotas {

    private final PagoRepository pagoRepository;

    /** En el mismo orden que {@code cuotas}. */
    public List<CuotaConSaldo> calcular(List<EstadoPrestamos> cuotas) {
        if (cuotas.isEmpty()) {
            return List.of();
        }
        Map<Integer, BigDecimal> abonos = pagoRepository
                .sumarVigentesPorCuota(cuotas.stream().map(EstadoPrestamos::getId).toList()).stream()
                .collect(Collectors.toMap(AbonoCuota::getIdCuota, abono -> Dinero.oCero(abono.getAbonado())));
        return cuotas.stream()
                .map(cuota -> {
                    BigDecimal abonado = abonos.getOrDefault(cuota.getId(), Dinero.CERO);
                    return new CuotaConSaldo(cuota, abonado, cuota.saldo(abonado));
                })
                .toList();
    }

    public static BigDecimal total(List<CuotaConSaldo> cuotas) {
        return cuotas.stream().map(CuotaConSaldo::saldo).reduce(Dinero.CERO, BigDecimal::add);
    }
}
