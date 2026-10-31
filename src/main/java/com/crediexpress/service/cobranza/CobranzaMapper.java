package com.crediexpress.service.cobranza;

import com.crediexpress.config.ClockConfig;
import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.entity.SituacionCuota;
import com.crediexpress.dto.repository.consulta.CuotaPendienteRepository;
import com.crediexpress.dto.view.response.ClienteResumenResponse;
import com.crediexpress.dto.view.response.CuotaCobranzaResponse;
import com.crediexpress.dto.view.response.LiquidacionResponse;
import com.crediexpress.dto.view.response.ModalidadResumenResponse;
import com.crediexpress.dto.view.response.PagoResponse;
import com.crediexpress.dto.view.response.SaldoPrestamoResponse;
import com.crediexpress.util.Dinero;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Entidades → respuestas de cobranza. Las entidades JPA nunca salen del servicio. */
@Component
public class CobranzaMapper {

    public ClienteResumenResponse cliente(Cliente cliente) {
        return new ClienteResumenResponse(cliente.getIdCliente(), cliente.getNombreCompleto(), cliente.getTelefono());
    }

    public ModalidadResumenResponse modalidad(Modalidad modalidad) {
        return new ModalidadResumenResponse(modalidad.getIdModalidad(), modalidad.getDescripcion());
    }

    /** Una cuota de la consulta de cobranza; la situación y los días se calculan con "hoy" de México. */
    public CuotaCobranzaResponse cuota(CuotaPendienteRepository.Cuota c, LocalDate hoy) {
        return new CuotaCobranzaResponse(
                c.idCuota(),
                c.idPrestamo(),
                new ClienteResumenResponse(c.idCliente(), c.cliente(), c.telefono()),
                new ModalidadResumenResponse(c.idModalidad(), c.modalidad()),
                c.numeroCuota(),
                c.totalCuotas(),
                c.fechaVencimiento(),
                Dinero.oCero(c.importe()),
                Dinero.oCero(c.abonado()),
                Dinero.oCero(c.saldo()),
                SituacionCuota.diasAtraso(c.fechaVencimiento(), hoy),
                SituacionCuota.diasParaVencer(c.fechaVencimiento(), hoy),
                SituacionCuota.de(c.fechaVencimiento(), hoy));
    }

    public SaldoPrestamoResponse saldoPrestamo(CalculadoraPrestamos prestamo, List<CuotaConSaldo> cuotas,
                                               LocalDate hoy) {
        List<SaldoPrestamoResponse.Cuota> detalle = cuotas.stream()
                .map(cuotaConSaldo -> {
                    EstadoPrestamos cuota = cuotaConSaldo.cuota();
                    return new SaldoPrestamoResponse.Cuota(cuota.getId(), cuota.getNumeroCuota(),
                            cuota.getFechaVencimiento(), cuota.importe(), cuotaConSaldo.abonado(),
                            cuotaConSaldo.saldo(), cuota.diasAtraso(hoy), cuota.diasParaVencer(hoy),
                            cuota.situacion(hoy));
                })
                .toList();
        return new SaldoPrestamoResponse(
                prestamo.getIdCalculadoraPrestamo(),
                cliente(prestamo.getIdCliente()),
                modalidad(prestamo.getIdModalidad()),
                prestamo.getNumeroCuotas(),
                cuotas.size(),
                SaldosCuotas.total(cuotas),
                cuotas.stream().anyMatch(cuotaConSaldo -> cuotaConSaldo.cuota().vencida(hoy)),
                detalle);
    }

    public PagoResponse pago(Pago pago) {
        return new PagoResponse(
                pago.getIdPago(),
                pago.getFolio(),
                pago.getCuota().getId(),
                pago.getPrestamo().getIdCalculadoraPrestamo(),
                cliente(pago.getCliente()),
                pago.getCuota().getNumeroCuota(),
                pago.getPrestamo().getNumeroCuotas(),
                pago.getImporte(),
                pago.getRecargo(),
                pago.totalRecibido(),
                pago.getFechaPago(),
                pago.getMetodoPago(),
                pago.getReferencia(),
                pago.getNota(),
                pago.getTipo(),
                pago.getSaldoCuota(),
                pago.cuotaLiquidada(),
                pago.isPrestamoLiquidado(),
                pago.getRegistradoPor(),
                horaMexico(pago.getFechaRegistro()),
                pago.getEstatus(),
                pago.getCanceladoPor(),
                horaMexico(pago.getFechaCancelacion()),
                pago.getMotivoCancelacion());
    }

    /** Los renglones de una liquidación, en orden de cuota. */
    public LiquidacionResponse liquidacion(List<Pago> renglones, boolean clienteSinAdeudos) {
        Pago primero = renglones.getFirst();
        BigDecimal importe = renglones.stream().map(Pago::getImporte).reduce(Dinero.CERO, BigDecimal::add);
        BigDecimal recargo = renglones.stream().map(Pago::getRecargo).reduce(Dinero.CERO, BigDecimal::add);
        List<LiquidacionResponse.Renglon> detalle = renglones.stream()
                .map(renglon -> new LiquidacionResponse.Renglon(renglon.getIdPago(), renglon.getCuota().getId(),
                        renglon.getCuota().getNumeroCuota(), renglon.getImporte()))
                .toList();
        return new LiquidacionResponse(
                primero.getFolio(),
                primero.getPrestamo().getIdCalculadoraPrestamo(),
                cliente(primero.getCliente()),
                renglones.size(),
                importe,
                recargo,
                importe.add(recargo),
                primero.getFechaPago(),
                primero.getMetodoPago(),
                primero.getReferencia(),
                primero.getNota(),
                detalle,
                renglones.getLast().isPrestamoLiquidado(),
                clienteSinAdeudos,
                primero.getRegistradoPor(),
                horaMexico(primero.getFechaRegistro()));
    }

    private static OffsetDateTime horaMexico(Instant instante) {
        return instante == null ? null : instante.atZone(ClockConfig.ZONA_NEGOCIO).toOffsetDateTime();
    }
}
