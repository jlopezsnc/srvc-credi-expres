package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Pago;
import com.crediexpress.dto.entity.TipoPago;
import com.crediexpress.dto.repository.CalculadoraPrestamosRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.view.request.CancelarPagoRequest;
import com.crediexpress.dto.view.request.LiquidarPrestamoRequest;
import com.crediexpress.dto.view.request.RegistrarPagoRequest;
import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.dto.view.response.LiquidacionResponse;
import com.crediexpress.dto.view.response.PagoResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.PagoService;
import com.crediexpress.service.cobranza.CobranzaMapper;
import com.crediexpress.service.cobranza.CuotaConSaldo;
import com.crediexpress.service.cobranza.DatosPago;
import com.crediexpress.service.cobranza.IdempotenciaPago;
import com.crediexpress.service.cobranza.IdempotenciaPago.Operacion;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.OperacionPago;
import com.crediexpress.service.cobranza.ResultadoOperacion;
import com.crediexpress.service.cobranza.SaldosCuotas;
import com.crediexpress.util.Dinero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.crediexpress.service.cobranza.MensajesCobranza.CUOTA_NO_EXISTE;
import static com.crediexpress.service.cobranza.MensajesCobranza.CUOTA_PAGADA;
import static com.crediexpress.service.cobranza.MensajesCobranza.MOTIVO_INVALIDO;
import static com.crediexpress.service.cobranza.MensajesCobranza.PAGO_EN_CONVENIO;
import static com.crediexpress.service.cobranza.MensajesCobranza.PAGO_NO_EXISTE;
import static com.crediexpress.service.cobranza.MensajesCobranza.PAGO_YA_CANCELADO;
import static com.crediexpress.service.cobranza.MensajesCobranza.PRESTAMO_CANCELADO;
import static com.crediexpress.service.cobranza.MensajesCobranza.PRESTAMO_EN_CONVENIO;
import static com.crediexpress.service.cobranza.MensajesCobranza.PRESTAMO_LIQUIDADO;
import static com.crediexpress.service.cobranza.MensajesCobranza.PRESTAMO_NO_EXISTE;
import static com.crediexpress.service.cobranza.MensajesCobranza.RECARGO_CUOTA_NO_VENCIDA;
import static com.crediexpress.service.cobranza.MensajesCobranza.RECARGO_SIN_VENCIDAS;
import static com.crediexpress.service.cobranza.MensajesCobranza.SOLO_CANCELAR_HOY;

/**
 * Cobro, liquidación y cancelación de pagos.
 *
 * <p>Concurrencia: cada operación bloquea ({@code FOR UPDATE}) las cuotas que va a tocar antes de
 * leer saldos, y corre en {@code READ_COMMITTED} para que, al obtener el bloqueo, las lecturas vean
 * lo que confirmó la transacción anterior. Así dos cobros a la misma cuota nunca superan su saldo y
 * un reintento con la misma llave encuentra el pago ya registrado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {

    static final int MINIMO_MOTIVO = 5;

    private final EstadoPrestamosRepository cuotaRepository;
    private final CalculadoraPrestamosRepository prestamoRepository;
    private final PagoRepository pagoRepository;
    private final IdempotenciaPago idempotencia;
    private final SaldosCuotas saldosCuotas;
    private final CobranzaMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoOperacion<PagoResponse> registrar(RegistrarPagoRequest request, LlaveIdempotencia llave,
                                                      String usuario) {
        LocalDate hoy = LocalDate.now(clock);
        DatosPago datos = DatosPago.validar(request, hoy, RegistrarPagoRequest.MENSAJE);
        Integer idPrestamo = cuotaRepository.findIdPrestamo(request.idCuota())
                .orElseThrow(() -> ApiException.noEncontrado(CUOTA_NO_EXISTE));
        List<EstadoPrestamos> conSaldo = cuotaRepository.bloquearConSaldoDelPrestamo(idPrestamo);

        String huella = IdempotenciaPago.huella(Operacion.REGISTRAR_PAGO, request.idCuota(), datos);
        List<Pago> previos = idempotencia.buscarRepetido(llave, huella);
        if (!previos.isEmpty()) {
            return ResultadoOperacion.repetida(mapper.pago(previos.getFirst()));
        }
        validarCobrable(idPrestamo);

        EstadoPrestamos cuota = conSaldo.stream()
                .filter(candidata -> candidata.getId().equals(request.idCuota()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflicto(CUOTA_PAGADA));
        EstadoPrestamos primeraPendiente = conSaldo.getFirst();
        if (!primeraPendiente.getId().equals(cuota.getId())) {
            throw ApiException.conflicto("Primero hay que cobrar la cuota " + primeraPendiente.getNumeroCuota()
                    + " de este préstamo.");
        }
        datos.validarRecargo(cuota.vencida(hoy), RegistrarPagoRequest.MENSAJE,
                RECARGO_CUOTA_NO_VENCIDA);

        CuotaConSaldo actual = saldosCuotas.calcular(List.of(cuota)).getFirst();
        if (datos.importe().compareTo(actual.saldo()) > 0) {
            throw ApiException.conflicto("El importe supera el saldo de la cuota (" + Dinero.texto(actual.saldo())
                    + "). Puede que alguien haya registrado un pago antes.");
        }

        BigDecimal saldoNuevo = cuota.aplicarAbonos(actual.abonado().add(datos.importe()), datos.fechaPago());
        boolean cuotaLiquidada = saldoNuevo.signum() == 0;
        boolean prestamoLiquidado = cuotaLiquidada && conSaldo.size() == 1;
        OperacionPago operacion = new OperacionPago(llave, huella, datos, usuario, clock.instant());
        Pago pago = pagoRepository.save(operacion.renglon(cuota, datos.importe(), datos.recargo(),
                cuotaLiquidada ? TipoPago.CUOTA_COMPLETA : TipoPago.ABONO_PARCIAL, saldoNuevo, prestamoLiquidado));
        pago.asignarFolio();

        log.info("Pago {} registrado por {}: cuota {}, préstamo {}, importe {}, recargo {}, saldo {}",
                pago.getFolio(), usuario, cuota.getId(), idPrestamo, datos.importe(), datos.recargo(), saldoNuevo);
        return ResultadoOperacion.nueva(mapper.pago(pago));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoOperacion<LiquidacionResponse> liquidar(LiquidarPrestamoRequest request, LlaveIdempotencia llave,
                                                            String usuario) {
        LocalDate hoy = LocalDate.now(clock);
        DatosPago datos = DatosPago.validar(request, hoy, LiquidarPrestamoRequest.MENSAJE);
        if (!prestamoRepository.existsById(request.idPrestamo())) {
            throw ApiException.noEncontrado(PRESTAMO_NO_EXISTE);
        }
        List<EstadoPrestamos> conSaldo = cuotaRepository.bloquearConSaldoDelPrestamo(request.idPrestamo());

        String huella = IdempotenciaPago.huella(Operacion.LIQUIDAR_PRESTAMO, request.idPrestamo(), datos);
        List<Pago> previos = idempotencia.buscarRepetido(llave, huella);
        if (!previos.isEmpty()) {
            return ResultadoOperacion.repetida(mapper.liquidacion(previos, clienteSinAdeudos(previos.getFirst())));
        }
        validarCobrable(request.idPrestamo());
        if (conSaldo.isEmpty()) {
            throw ApiException.conflicto(PRESTAMO_LIQUIDADO);
        }
        datos.validarRecargo(conSaldo.stream().anyMatch(cuota -> cuota.vencida(hoy)), LiquidarPrestamoRequest.MENSAJE,
                RECARGO_SIN_VENCIDAS);

        List<CuotaConSaldo> cuotas = saldosCuotas.calcular(conSaldo);
        BigDecimal saldoTotal = SaldosCuotas.total(cuotas);
        if (datos.importe().compareTo(saldoTotal) != 0) {
            throw ApiException.conflicto("El importe no coincide con el saldo del préstamo ("
                    + Dinero.texto(saldoTotal) + "). Actualiza la pantalla e intenta de nuevo.");
        }

        List<Pago> renglones = aplicarLiquidacion(cuotas,
                new OperacionPago(llave, huella, datos, usuario, clock.instant()));
        boolean sinAdeudos = clienteSinAdeudos(renglones.getFirst());

        log.info("Préstamo {} liquidado por {} con folio {}: {} cuotas, importe {}, recargo {}",
                request.idPrestamo(), usuario, renglones.getFirst().getFolio(), renglones.size(), datos.importe(),
                datos.recargo());
        return ResultadoOperacion.nueva(mapper.liquidacion(renglones, sinAdeudos));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PagoResponse cancelar(Integer idPago, CancelarPagoRequest request, String usuario) {
        String motivo = request.motivo().strip();
        if (motivo.length() < MINIMO_MOTIVO) {
            throw ApiException.datosInvalidos(MOTIVO_INVALIDO, List.of(new CampoError("motivo",
                    "Escribe el motivo de la cancelación (mínimo " + MINIMO_MOTIVO + " caracteres).")));
        }
        // Primero solo el folio: la entidad se carga ya bloqueada para no trabajar con datos viejos.
        String folio = pagoRepository.findFolio(idPago).orElseThrow(() -> ApiException.noEncontrado(PAGO_NO_EXISTE));
        List<Pago> operacion = pagoRepository.bloquearPorFolio(folio);
        Pago solicitado = operacion.stream()
                .filter(pago -> pago.getIdPago().equals(idPago))
                .findFirst()
                .orElseThrow(() -> ApiException.noEncontrado(PAGO_NO_EXISTE));
        if (!solicitado.vigente()) {
            throw ApiException.conflicto(PAGO_YA_CANCELADO);
        }
        LocalDate diaRegistro = LocalDate.ofInstant(solicitado.getFechaRegistro(), clock.getZone());
        if (!diaRegistro.equals(LocalDate.now(clock))) {
            throw ApiException.conflicto(SOLO_CANCELAR_HOY);
        }

        Instant ahora = clock.instant();
        List<Pago> vigentes = operacion.stream().filter(Pago::vigente).toList();
        vigentes.forEach(pago -> pago.cancelar(usuario, ahora, motivo));

        // La consulta de abonos hace flush antes: ya no cuenta los pagos recién cancelados.
        List<EstadoPrestamos> cuotas = cuotaRepository.bloquear(
                vigentes.stream().map(pago -> pago.getCuota().getId()).distinct().toList());
        // Su saldo ya se convino con estos pagos: deshacerlos cambiaría el convenio (se revierte todo).
        if (cuotas.stream().anyMatch(EstadoPrestamos::enConvenio)) {
            throw ApiException.conflicto(PAGO_EN_CONVENIO);
        }
        saldosCuotas.calcular(cuotas)
                .forEach(cuota -> cuota.cuota().aplicarAbonos(cuota.abonado(), null));

        log.info("Pago {} cancelado por {} ({} renglones): {}", folio, usuario, vigentes.size(), motivo);
        return mapper.pago(solicitado);
    }

    /**
     * Un renglón de {@code pago} por cuota con saldo, de la más antigua a la más reciente, con el
     * mismo folio. El recargo va solo en el primero.
     */
    private List<Pago> aplicarLiquidacion(List<CuotaConSaldo> cuotas, OperacionPago operacion) {
        List<CuotaConSaldo> porCobrar = cuotas.stream().filter(cuota -> cuota.saldo().signum() > 0).toList();
        List<Pago> renglones = new ArrayList<>(porCobrar.size());
        String folio = null;
        for (CuotaConSaldo cuota : porCobrar) {
            boolean primero = renglones.isEmpty();
            boolean ultimo = renglones.size() == porCobrar.size() - 1;
            Pago renglon = pagoRepository.save(operacion.renglon(cuota.cuota(), cuota.saldo(),
                    primero ? operacion.datos().recargo() : Dinero.CERO, TipoPago.LIQUIDACION, Dinero.CERO, ultimo));
            if (primero) {
                folio = renglon.asignarFolio();
            } else {
                renglon.compartirFolio(folio);
            }
            renglones.add(renglon);
        }
        // Todas quedan pagadas, incluidas las que ya tenían saldo cero por un dato inconsistente.
        cuotas.forEach(cuota -> cuota.cuota().aplicarAbonos(cuota.cuota().importe(), operacion.datos().fechaPago()));
        return renglones;
    }

    /** Un préstamo en convenio o de un convenio cancelado ya no se cobra (las cuotas ya están bloqueadas). */
    private void validarCobrable(Integer idPrestamo) {
        if (cuotaRepository.prestamoEnConvenio(idPrestamo)) {
            throw ApiException.conflicto(PRESTAMO_EN_CONVENIO);
        }
        if (cuotaRepository.prestamoCancelado(idPrestamo)) {
            throw ApiException.conflicto(PRESTAMO_CANCELADO);
        }
    }

    private boolean clienteSinAdeudos(Pago pago) {
        return !cuotaRepository.clienteTieneSaldo(pago.getCliente().getIdCliente());
    }
}
