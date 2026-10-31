package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.repository.spec.CuotaSpecs;
import com.crediexpress.dto.repository.spec.PagoSpecs;
import com.crediexpress.dto.view.response.ReporteCarteraResponse;
import com.crediexpress.dto.view.response.ReporteCobranzaResponse;
import com.crediexpress.service.ReporteService;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.SaldosCuotas;
import com.crediexpress.service.reporte.PeriodoReporte;
import com.crediexpress.service.reporte.ReporteCartera;
import com.crediexpress.service.reporte.ReporteCobranza;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteServiceImpl implements ReporteService {

    static final Sort ORDEN_PAGOS = Sort.by("fechaRegistro", "idPago");
    static final Sort ORDEN_CUOTAS = Sort.by("fechaVencimiento", "id");

    private final PagoRepository pagoRepository;
    private final EstadoPrestamosRepository cuotaRepository;
    private final SaldosCuotas saldosCuotas;
    private final CobranzaMapper mapper;
    private final Clock clock;

    @Override
    public ReporteCobranzaResponse cobranza(LocalDate desde, LocalDate hasta) {
        PeriodoReporte periodo = PeriodoReporte.de(desde, hasta, LocalDate.now(clock));
        List<Pago> pagos = pagoRepository.findAll(
                PagoSpecs.registradosEntre(periodo.inicio(clock.getZone()), periodo.fin(clock.getZone())),
                ORDEN_PAGOS);
        return ReporteCobranza.armar(periodo, pagos, mapper);
    }

    @Override
    public ReporteCarteraResponse cartera() {
        return ReporteCartera.armar(LocalDate.now(clock),
                saldosCuotas.calcular(cuotaRepository.findAll(CuotaSpecs.conSaldo(), ORDEN_CUOTAS)), mapper);
    }
}
