package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.MovimientoCaja;
import com.crediexpress.dto.repository.MovimientoCajaRepository;
import com.crediexpress.dto.repository.consulta.CajaRepository;
import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.RegistrarMovimientoCajaRequest;
import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.dto.view.response.MovimientoCajaResponse;
import com.crediexpress.dto.view.response.PaginaResponse;
import com.crediexpress.dto.view.response.SaldoCajaResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.CajaService;
import com.crediexpress.service.caja.MensajesCaja;
import com.crediexpress.service.caja.SaldoCaja;
import com.crediexpress.util.Dinero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CajaServiceImpl implements CajaService {

    static final Sort ORDEN_MOVIMIENTOS = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("idMovimiento"));

    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public SaldoCajaResponse saldo(Integer anio) {
        if (anio != null && (anio < 2000 || anio > 2100)) {
            throw ApiException.datosInvalidos(MensajesCaja.ANIO_INVALIDO,
                    List.of(new CampoError("anio", MensajesCaja.ANIO_INVALIDO)));
        }
        SaldoCaja.Sumas sumas = new SaldoCaja.Sumas(cajaRepository.aportadoPorMes(), cajaRepository.cobradoPorMes(),
                cajaRepository.prestadoPorMes(), cajaRepository.retiradoPorMes());
        return SaldoCaja.armar(sumas, anio, YearMonth.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<MovimientoCajaResponse> movimientos(int pagina, int tamano) {
        return PaginaResponse.de(movimientoRepository.findAll(PageRequest.of(pagina, tamano, ORDEN_MOVIMIENTOS)),
                MovimientoCajaResponse::de);
    }

    @Override
    @Transactional
    public MovimientoCajaResponse registrar(RegistrarMovimientoCajaRequest request, String usuario) {
        if (request.fecha().isAfter(LocalDate.now(clock))) {
            throw ApiException.datosInvalidos(RegistrarMovimientoCajaRequest.MENSAJE,
                    List.of(new CampoError("fecha", MensajesCaja.FECHA_FUTURA)));
        }
        MovimientoCaja movimiento = MovimientoCaja.builder()
                .tipo(request.tipo())
                .importe(Dinero.de(request.importe()))
                .fecha(request.fecha())
                .concepto(request.concepto().strip())
                .registradoPor(usuario)
                .fechaRegistro(Instant.now(clock))
                .build();
        return MovimientoCajaResponse.de(movimientoRepository.save(movimiento));
    }

    @Override
    @Transactional
    public MovimientoCajaResponse cancelar(Integer idMovimiento, CancelarPagoRequest request, String usuario) {
        MovimientoCaja movimiento = movimientoRepository.findById(idMovimiento)
                .orElseThrow(() -> ApiException.noEncontrado(MensajesCaja.MOVIMIENTO_NO_EXISTE));
        if (!movimiento.vigente()) {
            throw ApiException.conflicto(MensajesCaja.YA_CANCELADO);
        }
        movimiento.cancelar(usuario, Instant.now(clock), request.motivo().strip());
        return MovimientoCajaResponse.de(movimiento);
    }
}
