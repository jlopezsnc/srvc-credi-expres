package com.crediexpress.service.convenio;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.Convenio;
import com.crediexpress.dto.entity.ConvenioPrestamo;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.repository.consulta.ConvenioConsultaRepository.Fila;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Prestamo;
import com.crediexpress.dto.view.response.ClienteResumenResponse;
import com.crediexpress.dto.view.response.ConvenioDetalleResponse;
import com.crediexpress.dto.view.response.ConvenioResponse;
import com.crediexpress.dto.view.response.ConvenioResumenResponse;
import com.crediexpress.dto.view.response.PrestamoDetalleResponse;
import com.crediexpress.dto.view.response.ModalidadResumenResponse;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse.CuotaPlan;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse.PrestamoConvenido;
import com.crediexpress.service.CalculadoraPrestamo.Plan;
import com.crediexpress.util.Dinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Arma las respuestas de convenios: la simulación, el convenio guardado, la lista y el detalle. */
public final class ConvenioMapper {

    private ConvenioMapper() {
    }

    public static SimulacionConvenioResponse simulacion(List<Prestamo> prestamos, Modalidad modalidad, Plan plan) {
        List<PrestamoConvenido> convenidos = prestamos.stream()
                .map(p -> new PrestamoConvenido(p.idPrestamo(), new ClienteResumenResponse(p.idCliente(), p.cliente(),
                        p.telefono()), p.situacion(), p.numeroCuotas(), p.cuotasPagadas(), p.cuotasVencidas(),
                        Dinero.oCero(p.saldo())))
                .toList();
        long personas = prestamos.stream().map(Prestamo::idCliente).distinct().count();
        List<CuotaPlan> calendario = plan.cuotas().stream()
                .map(c -> new CuotaPlan(c.numero(), c.fechaVencimiento(), c.importe()))
                .toList();
        return plan(convenidos, (int) personas, modalidad, plan.monto(), plan.tasaMensual(), plan.meses(),
                plan.interes(), plan.total(), plan.importeCuota(), calendario);
    }

    /** El convenio tal como quedó guardado; {@code calendario} son las cuotas del préstamo nuevo. */
    public static ConvenioResponse convenio(Convenio convenio, List<EstadoPrestamos> calendario, BigDecimal meses) {
        List<PrestamoConvenido> convenidos = convenio.getPrestamos().stream()
                .map(ConvenioMapper::convenido)
                .toList();
        long personas = convenio.getPrestamos().stream()
                .map(p -> p.getCliente().getIdCliente()).distinct().count();
        List<CuotaPlan> cuotas = calendario.stream()
                .map(c -> new CuotaPlan(c.getNumeroCuota(), c.getFechaVencimiento(), c.importe()))
                .toList();
        SimulacionConvenioResponse plan = plan(convenidos, (int) personas, convenio.getModalidad(),
                convenio.getSaldoConvenido(), convenio.getTasaInteres(), meses, convenio.getInteres(),
                convenio.getTotalPagar(), convenio.getImporteCuota(), cuotas);
        return new ConvenioResponse(
                convenio.getIdConvenio(),
                convenio.getFolio(),
                convenio.getPrestamoConvenio().getIdCalculadoraPrestamo(),
                cliente(convenio.getTitular()),
                convenio.getMotivo(),
                convenio.getEstatus(),
                convenio.getRegistradoPor(),
                convenio.getFechaRegistro(),
                plan);
    }

    /** Un renglón de la lista, como lo calculó la consulta. */
    public static ConvenioResumenResponse resumen(Fila f) {
        return new ConvenioResumenResponse(
                f.idConvenio(),
                f.folio(),
                new ClienteResumenResponse(f.idTitular(), f.titular(), f.telefono()),
                f.idPrestamoConvenio(),
                f.prestamos(),
                f.personas(),
                new ModalidadResumenResponse(f.idModalidad(), f.modalidad()),
                f.numeroPagos(),
                f.cuotasPagadas(),
                f.cuotasVencidas(),
                Dinero.oCero(f.saldoConvenido()),
                Dinero.oCero(f.totalPagar()),
                Dinero.oCero(f.importeCuota()),
                Dinero.oCero(f.saldoPendiente()),
                f.situacion(),
                f.estatus(),
                f.fechaRegistro());
    }

    /** El detalle: resumen calculado, plan guardado, préstamos de origen, calendario y bitácora. */
    public static ConvenioDetalleResponse detalle(Fila fila, Convenio convenio, BigDecimal meses,
                                                  List<PrestamoDetalleResponse.Cuota> calendario,
                                                  long pagosRegistrados, String motivoNoCancelable) {
        return new ConvenioDetalleResponse(
                resumen(fila),
                convenio.getTasaInteres().setScale(2, RoundingMode.HALF_UP),
                meses.setScale(Math.max(0, meses.stripTrailingZeros().scale()), RoundingMode.HALF_UP),
                Dinero.de(convenio.getInteres()),
                convenio.getFechaPrimerPago(),
                convenio.getMotivo(),
                convenio.getPrestamos().stream().map(ConvenioMapper::convenido).toList(),
                calendario,
                pagosRegistrados,
                motivoNoCancelable == null,
                motivoNoCancelable,
                new ConvenioDetalleResponse.Bitacora(
                        convenio.getRegistradoPor(),
                        convenio.getFechaRegistro(),
                        convenio.getIpOrigen(),
                        convenio.getActualizadoPor(),
                        convenio.getFechaActualizacion(),
                        convenio.getCanceladoPor(),
                        convenio.getFechaCancelacion(),
                        convenio.getMotivoCancelacion()));
    }

    /** Plazo en meses con hasta dos decimales y sin ceros de más (3, 2.5). */
    public static BigDecimal meses(int numeroPagos, int pagosPorMes) {
        return BigDecimal.valueOf(numeroPagos)
                .divide(BigDecimal.valueOf(pagosPorMes), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static SimulacionConvenioResponse plan(List<PrestamoConvenido> convenidos, int personas,
                                                   Modalidad modalidad, BigDecimal saldo, BigDecimal tasa,
                                                   BigDecimal meses, BigDecimal interes, BigDecimal total,
                                                   BigDecimal importeCuota, List<CuotaPlan> calendario) {
        return new SimulacionConvenioResponse(
                convenidos,
                personas,
                Dinero.de(saldo),
                tasa.setScale(2, RoundingMode.HALF_UP),
                meses.setScale(Math.max(0, meses.stripTrailingZeros().scale()), RoundingMode.HALF_UP),
                Dinero.de(interes),
                Dinero.de(total),
                calendario.size(),
                Dinero.de(importeCuota),
                calendario.isEmpty() ? Dinero.CERO : calendario.getLast().importe(),
                new ModalidadResumenResponse(modalidad.getIdModalidad(), modalidad.getDescripcion()),
                calendario);
    }

    private static PrestamoConvenido convenido(ConvenioPrestamo p) {
        int numeroCuotas = p.getPrestamo().getNumeroCuotas();
        return new PrestamoConvenido(
                p.getPrestamo().getIdCalculadoraPrestamo(),
                cliente(p.getCliente()),
                p.getSituacionAnterior(),
                numeroCuotas,
                numeroCuotas - p.getCuotasConvenidas(),
                p.getCuotasVencidas(),
                p.getSaldoConvenido());
    }

    private static ClienteResumenResponse cliente(Cliente cliente) {
        return new ClienteResumenResponse(cliente.getIdCliente(), cliente.getNombreCompleto(), cliente.getTelefono());
    }
}
