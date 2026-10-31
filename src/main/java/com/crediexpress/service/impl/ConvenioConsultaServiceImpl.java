package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Convenio;
import com.crediexpress.dto.entity.SituacionConvenio;
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
import com.crediexpress.service.CalculadoraPrestamo.Periodicidad;
import com.crediexpress.service.ConvenioConsultaService;
import com.crediexpress.service.PrestamoConsultaService;
import com.crediexpress.service.convenio.ConvenioMapper;
import com.crediexpress.service.convenio.ReglaCancelacionConvenio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConvenioConsultaServiceImpl implements ConvenioConsultaService {

    static final String CONVENIO_NO_EXISTE = "El convenio no existe.";

    private final ConvenioConsultaRepository consultaRepository;
    private final ConvenioRepository convenioRepository;
    private final PagoRepository pagoRepository;
    private final PrestamoConsultaService prestamoConsultaService;
    private final Clock clock;

    @Override
    public PaginaConveniosResponse listar(FiltroConvenios filtro, String busqueda, OrdenConvenios orden,
                                          Direccion direccion, int pagina, int tamano) {
        LocalDate hoy = LocalDate.now(clock);
        Consulta consulta = new Consulta(filtro, busqueda, orden, direccion, pagina, tamano, hoy);
        return PaginaConveniosResponse.de(
                consultaRepository.buscar(consulta).stream().map(ConvenioMapper::resumen).toList(),
                pagina, tamano, consultaRepository.contar(consulta),
                contadores(consultaRepository.contarPorSituacion(hoy)));
    }

    @Override
    public ConvenioDetalleResponse detalle(Integer idConvenio) {
        LocalDate hoy = LocalDate.now(clock);
        Fila fila = consultaRepository.buscarPorId(idConvenio, hoy)
                .orElseThrow(() -> ApiException.noEncontrado(CONVENIO_NO_EXISTE));
        Convenio convenio = convenioRepository.buscarDetalle(idConvenio)
                .orElseThrow(() -> ApiException.noEncontrado(CONVENIO_NO_EXISTE));
        Integer idPrestamo = convenio.getPrestamoConvenio().getIdCalculadoraPrestamo();
        List<PrestamoDetalleResponse.Cuota> calendario = prestamoConsultaService.detalle(idPrestamo).cuotas();
        long pagos = pagoRepository.contarPagosVigentesDelPrestamo(idPrestamo);
        int pagosPorMes = Periodicidad.deDias(convenio.getModalidad().getDiasActivos())
                .map(Periodicidad::pagosPorMes)
                .orElse(1);
        return ConvenioMapper.detalle(fila, convenio, ConvenioMapper.meses(convenio.getNumeroPagos(), pagosPorMes),
                calendario, pagos, ReglaCancelacionConvenio.loImpide(convenio.vigente(), pagos).orElse(null));
    }

    private static PaginaConveniosResponse.Contadores contadores(Map<SituacionConvenio, Long> cuentas) {
        long alCorriente = cuentas.getOrDefault(SituacionConvenio.AL_CORRIENTE, 0L);
        long conAtraso = cuentas.getOrDefault(SituacionConvenio.CON_ATRASO, 0L);
        long liquidados = cuentas.getOrDefault(SituacionConvenio.LIQUIDADO, 0L);
        long cancelados = cuentas.getOrDefault(SituacionConvenio.CANCELADO, 0L);
        return new PaginaConveniosResponse.Contadores(alCorriente + conAtraso, conAtraso, liquidados, cancelados,
                alCorriente + conAtraso + liquidados + cancelados);
    }
}
