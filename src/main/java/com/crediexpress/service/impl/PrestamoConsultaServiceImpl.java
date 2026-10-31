package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.EstatusCuota;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Consulta;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Cuota;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Prestamo;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import com.crediexpress.dto.view.response.ClienteResumenResponse;
import com.crediexpress.dto.view.response.ModalidadResumenResponse;
import com.crediexpress.dto.view.response.PaginaPrestamosResponse;
import com.crediexpress.dto.view.response.PrestamoDetalleResponse;
import com.crediexpress.dto.view.response.PrestamoResumenResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.PrestamoConsultaService;
import com.crediexpress.service.cobranza.MensajesCobranza;
import com.crediexpress.util.Dinero;
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
public class PrestamoConsultaServiceImpl implements PrestamoConsultaService {

    private final PrestamoConsultaRepository repository;
    private final Clock clock;

    @Override
    public PaginaPrestamosResponse listar(FiltroPrestamos filtro, String busqueda, Integer idCliente,
                                          OrdenPrestamos orden,
                                          Direccion direccion, int pagina, int tamano) {
        LocalDate hoy = LocalDate.now(clock);
        Consulta consulta = new Consulta(filtro, busqueda, orden, direccion, pagina, tamano, hoy, idCliente);
        List<PrestamoResumenResponse> contenido = repository.buscar(consulta).stream()
                .map(PrestamoConsultaServiceImpl::resumen)
                .toList();
        return PaginaPrestamosResponse.de(contenido, pagina, tamano, repository.contar(consulta),
                contadores(repository.contarPorSituacion(hoy)));
    }

    @Override
    public PrestamoDetalleResponse detalle(Integer idPrestamo) {
        LocalDate hoy = LocalDate.now(clock);
        Prestamo prestamo = repository.buscarPorId(idPrestamo, hoy)
                .orElseThrow(() -> ApiException.noEncontrado(MensajesCobranza.PRESTAMO_NO_EXISTE));
        List<PrestamoDetalleResponse.Cuota> cuotas = repository.cuotasDe(idPrestamo).stream()
                .map(cuota -> cuota(cuota, hoy))
                .toList();
        return new PrestamoDetalleResponse(resumen(prestamo), cuotas);
    }

    private static PaginaPrestamosResponse.Contadores contadores(Map<SituacionPrestamo, Long> cuentas) {
        long alCorriente = cuentas.getOrDefault(SituacionPrestamo.AL_CORRIENTE, 0L);
        long conAtraso = cuentas.getOrDefault(SituacionPrestamo.CON_ATRASO, 0L);
        long liquidados = cuentas.getOrDefault(SituacionPrestamo.LIQUIDADO, 0L);
        long enConvenio = cuentas.getOrDefault(SituacionPrestamo.EN_CONVENIO, 0L);
        long cancelados = cuentas.getOrDefault(SituacionPrestamo.CANCELADO, 0L);
        return new PaginaPrestamosResponse.Contadores(alCorriente + conAtraso, alCorriente, conAtraso, liquidados,
                enConvenio, cancelados, alCorriente + conAtraso + liquidados + enConvenio + cancelados);
    }

    private static PrestamoResumenResponse resumen(Prestamo p) {
        return new PrestamoResumenResponse(
                p.idPrestamo(),
                new ClienteResumenResponse(p.idCliente(), p.cliente(), p.telefono()),
                new ModalidadResumenResponse(p.idModalidad(), p.modalidad()),
                p.numeroCuotas(),
                p.cuotasPagadas(),
                p.cuotasVencidas(),
                Dinero.oCero(p.importeCuota()),
                Dinero.oCero(p.total()),
                Dinero.oCero(p.saldo()),
                p.proximoPago(),
                p.proximaCuota(),
                p.situacion(),
                p.convenioDestino(),
                p.convenioOrigen());
    }

    private static PrestamoDetalleResponse.Cuota cuota(Cuota c, LocalDate hoy) {
        return new PrestamoDetalleResponse.Cuota(
                c.idCuota(),
                c.numeroCuota(),
                c.fechaVencimiento(),
                Dinero.oCero(c.importe()),
                Dinero.oCero(c.abonado()),
                Dinero.oCero(c.saldo()),
                c.fechaPago(),
                EstatusCuota.de(c.estado(), c.abonado(), c.saldo(), c.fechaVencimiento(), hoy));
    }
}
