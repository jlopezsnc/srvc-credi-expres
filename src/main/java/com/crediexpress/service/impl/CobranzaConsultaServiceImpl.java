package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.entity.SituacionCuota;
import com.crediexpress.dto.repository.CalculadoraPrestamosRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository.TotalCuotas;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.repository.PagoRepository.TotalCobrado;
import com.crediexpress.dto.repository.consulta.CuotaPendienteRepository;
import com.crediexpress.dto.repository.consulta.CuotaPendienteRepository.Consulta;
import com.crediexpress.dto.repository.consulta.CuotaPendienteRepository.Rango;
import com.crediexpress.dto.repository.spec.PagoSpecs;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.OrdenCuotas;
import com.crediexpress.dto.view.response.CuotaCobranzaResponse;
import com.crediexpress.dto.view.response.OperacionPagoResponse;
import com.crediexpress.dto.view.response.PaginaConTotalResponse;
import com.crediexpress.dto.view.response.PaginaResponse;
import com.crediexpress.dto.view.response.PagoResponse;
import com.crediexpress.dto.view.response.ResumenCobranzaResponse;
import com.crediexpress.dto.view.response.SaldoPrestamoResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.CobranzaConsultaService;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.CuotaConSaldo;
import com.crediexpress.service.cobranza.MensajesCobranza;
import com.crediexpress.service.cobranza.OperacionesPago;
import com.crediexpress.service.cobranza.SaldosCuotas;
import com.crediexpress.util.Dinero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CobranzaConsultaServiceImpl implements CobranzaConsultaService {

    static final Sort ORDEN_PAGOS = Sort.by(Sort.Direction.DESC, "fechaRegistro", "idPago");

    private final EstadoPrestamosRepository cuotaRepository;
    private final CuotaPendienteRepository cuotaPendienteRepository;
    private final CalculadoraPrestamosRepository prestamoRepository;
    private final PagoRepository pagoRepository;
    private final SaldosCuotas saldosCuotas;
    private final CobranzaMapper mapper;
    private final Clock clock;

    @Override
    public ResumenCobranzaResponse resumen(LocalDate fecha, int dias) {
        LocalDate dia = fecha == null ? LocalDate.now(clock) : fecha;
        TotalCobrado cobrado = pagoRepository.totalCobrado(inicioDe(dia), inicioDe(dia.plusDays(1)));
        return new ResumenCobranzaResponse(dia,
                cuotas(cuotaRepository.totalQueVence(dia)),
                cuotas(cuotaRepository.totalVencidoAntesDe(dia)),
                cuotas(cuotaRepository.totalQueVenceEntre(dia.plusDays(1), dia.plusDays(dias))),
                new ResumenCobranzaResponse.Cobrado(cobrado.getPagos(), Dinero.de(cobrado.getImporte())));
    }

    @Override
    public PaginaConTotalResponse<CuotaCobranzaResponse> cuotasPendientes(SituacionCuota situacion, int dias,
                                                                          String busqueda, OrdenCuotas orden,
                                                                          Direccion direccion, int pagina,
                                                                          int tamano) {
        LocalDate hoy = LocalDate.now(clock);
        Rango rango = rango(situacion, dias, hoy);
        List<CuotaCobranzaResponse> contenido = cuotaPendienteRepository
                .buscar(new Consulta(rango, busqueda, orden, direccion, pagina, tamano)).stream()
                .map(cuota -> mapper.cuota(cuota, hoy))
                .toList();
        CuotaPendienteRepository.Total total = cuotaPendienteRepository.totalizar(rango, busqueda);
        return PaginaConTotalResponse.de(contenido, pagina, tamano, total.cuotas(), Dinero.oCero(total.saldo()));
    }

    @Override
    public PaginaResponse<PagoResponse> pagos(LocalDate fecha, EstatusPago estatus, String busqueda, int pagina,
                                              int tamano) {
        LocalDate dia = fecha == null ? LocalDate.now(clock) : fecha;
        Specification<Pago> filtro = PagoSpecs.registradosEntre(inicioDe(dia), inicioDe(dia.plusDays(1)))
                .and(PagoSpecs.conEstatus(estatus))
                .and(PagoSpecs.clienteContiene(busqueda));
        return PaginaResponse.de(pagoRepository.findAll(filtro, PageRequest.of(pagina, tamano, ORDEN_PAGOS)),
                mapper::pago);
    }

    /**
     * Las operaciones de un día se agrupan por folio en memoria: un día de cobranza son decenas de
     * renglones, no miles. Para un rango largo está el reporte de cobranza.
     */
    @Override
    public PaginaConTotalResponse<OperacionPagoResponse> operaciones(LocalDate fecha, String busqueda, int pagina,
                                                                     int tamano) {
        LocalDate dia = fecha == null ? LocalDate.now(clock) : fecha;
        Specification<Pago> filtro = PagoSpecs.registradosEntre(inicioDe(dia), inicioDe(dia.plusDays(1)))
                .and(PagoSpecs.conEstatus(EstatusPago.VIGENTE))
                .and(PagoSpecs.clienteContiene(busqueda));
        List<OperacionPagoResponse> operaciones =
                OperacionesPago.agrupar(pagoRepository.findAll(filtro, ORDEN_PAGOS), mapper);
        return PaginaConTotalResponse.cortar(operaciones, pagina, tamano, OperacionesPago.totalRecibido(operaciones));
    }

    @Override
    public SaldoPrestamoResponse saldoPrestamo(Integer idPrestamo) {
        CalculadoraPrestamos prestamo = prestamoRepository.findById(idPrestamo)
                .orElseThrow(() -> ApiException.noEncontrado(MensajesCobranza.PRESTAMO_NO_EXISTE));
        List<CuotaConSaldo> cuotas = saldosCuotas.calcular(cuotaRepository.findConSaldoDelPrestamo(idPrestamo));
        if (cuotas.isEmpty()) {
            throw ApiException.conflicto(MensajesCobranza.PRESTAMO_LIQUIDADO);
        }
        return mapper.saldoPrestamo(prestamo, cuotas, LocalDate.now(clock));
    }

    private static Rango rango(SituacionCuota situacion, int dias, LocalDate hoy) {
        return switch (situacion) {
            case VENCIDA -> new Rango(null, hoy.minusDays(1));
            case HOY -> new Rango(hoy, hoy);
            case PROXIMA -> new Rango(hoy.plusDays(1), hoy.plusDays(dias));
        };
    }

    /** Medianoche en México como instante (fecha_registro se guarda en UTC). */
    private Instant inicioDe(LocalDate dia) {
        return dia.atStartOfDay(clock.getZone()).toInstant();
    }

    private static ResumenCobranzaResponse.Cuotas cuotas(TotalCuotas total) {
        return new ResumenCobranzaResponse.Cuotas(total.getCuotas(), Dinero.de(total.getImporte()));
    }
}
