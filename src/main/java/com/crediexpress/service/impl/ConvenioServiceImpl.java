package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.Convenio;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.ConvenioPrestamo;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.repository.ConvenioRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.ModalidadRepository;
import com.crediexpress.dto.repository.PagoRepository;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Prestamo;
import com.crediexpress.dto.view.request.CancelarConvenioRequest;
import com.crediexpress.dto.view.request.GenerarConvenioRequest;
import com.crediexpress.dto.view.request.PlanConvenio;
import com.crediexpress.dto.view.request.SimularConvenioRequest;
import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.dto.view.response.ConvenioDetalleResponse;
import com.crediexpress.dto.view.response.ConvenioResponse;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.CalculadoraPrestamo;
import com.crediexpress.service.CalculadoraPrestamo.Periodicidad;
import com.crediexpress.service.CalculadoraPrestamo.Plan;
import com.crediexpress.service.ConvenioConsultaService;
import com.crediexpress.service.ConvenioService;
import com.crediexpress.service.cobranza.CuotaConSaldo;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.ResultadoOperacion;
import com.crediexpress.service.cobranza.SaldosCuotas;
import com.crediexpress.service.convenio.ConvenioMapper;
import com.crediexpress.service.convenio.HuellaConvenio;
import com.crediexpress.service.convenio.ReglaCancelacionConvenio;
import com.crediexpress.service.prestamo.RegistroPrestamo;
import com.crediexpress.service.prestamo.RegistroPrestamo.Registrado;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Convenios: varios préstamos con saldo (vigentes o vencidos, de uno o varios clientes) se juntan
 * en un préstamo nuevo a nombre del titular, sin interés salvo casos excepcionales.
 *
 * <p>Concurrencia: igual que en cobranza, primero se bloquean ({@code FOR UPDATE}) las cuotas con
 * saldo de los préstamos, siempre en orden de id, y luego se leen los saldos. Un pago que llegue a la
 * vez espera; si se registró antes, el saldo ya no coincide con {@code saldoEsperado} y se responde
 * 409 en lugar de convenir otra cantidad. Cancelar bloquea primero el convenio y luego las cuotas
 * (las del préstamo nuevo y las de los préstamos de origen, en orden de id).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConvenioServiceImpl implements ConvenioService {

    static final String TITULAR_NO_EXISTE = "El titular no existe.";
    static final String CONVENIO_NO_EXISTE = "El convenio no existe.";
    static final int MINIMO_MOTIVO = 10;
    static final String LLAVE_REUTILIZADA =
            "Este identificador ya se usó para otro convenio. Recarga la página e intenta de nuevo.";

    private final PrestamoConsultaRepository consultaRepository;
    private final EstadoPrestamosRepository cuotaRepository;
    private final ClienteRepository clienteRepository;
    private final ModalidadRepository modalidadRepository;
    private final ConvenioRepository convenioRepository;
    private final SaldosCuotas saldosCuotas;
    private final RegistroPrestamo registroPrestamo;
    private final PagoRepository pagoRepository;
    private final ConvenioConsultaService consultaService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public SimulacionConvenioResponse simular(SimularConvenioRequest request) {
        LocalDate hoy = LocalDate.now(clock);
        Condiciones condiciones = condiciones(request, hoy);
        List<Prestamo> prestamos = convenibles(condiciones.idsPrestamo(), hoy);
        BigDecimal saldo = prestamos.stream().map(p -> Dinero.oCero(p.saldo())).reduce(Dinero.CERO, BigDecimal::add);
        return ConvenioMapper.simulacion(prestamos, condiciones.modalidad(), plan(saldo, request, condiciones));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoOperacion<ConvenioResponse> generar(GenerarConvenioRequest request, LlaveIdempotencia llave,
                                                        String usuario, String ipOrigen) {
        LocalDate hoy = LocalDate.now(clock);
        Cliente titular = clienteRepository.findById(request.idClienteTitular())
                .orElseThrow(() -> ApiException.noEncontrado(TITULAR_NO_EXISTE));
        Condiciones condiciones = condiciones(request, hoy);

        // Bloqueo antes de leer, en orden de id (sin bloqueos cruzados con cobranza ni entre convenios).
        List<List<EstadoPrestamos>> bloqueadas = condiciones.idsPrestamo().stream()
                .sorted()
                .map(cuotaRepository::bloquearConSaldoDelPrestamo)
                .toList();

        // Con los préstamos ya bloqueados: si la misma petición llegó dos veces a la vez, aquí la
        // segunda encuentra lo que guardó la primera.
        String huella = HuellaConvenio.de(request);
        Optional<Convenio> previo = convenioRepository.findByLlaveIdempotencia(llave.valor());
        if (previo.isPresent()) {
            if (!previo.get().getHashPeticion().equals(huella)) {
                throw ApiException.conflicto(LLAVE_REUTILIZADA);
            }
            return ResultadoOperacion.repetida(respuesta(previo.get(),
                    cuotaRepository.cuotasDelPrestamo(previo.get().getPrestamoConvenio().getIdCalculadoraPrestamo())));
        }

        List<Prestamo> prestamos = convenibles(condiciones.idsPrestamo().stream().sorted().toList(), hoy);

        Instant ahora = clock.instant();
        List<ConvenioPrestamo> origenes = new ArrayList<>(prestamos.size());
        List<EstadoPrestamos> porConvenir = new ArrayList<>();
        BigDecimal saldo = Dinero.CERO;
        for (int i = 0; i < prestamos.size(); i++) {
            Prestamo prestamo = prestamos.get(i);
            List<CuotaConSaldo> cuotas = saldosCuotas.calcular(bloqueadas.get(i)).stream()
                    .filter(cuota -> cuota.saldo().signum() > 0)
                    .toList();
            if (cuotas.isEmpty()) {
                throw ApiException.conflicto("El préstamo #" + prestamo.idPrestamo() + " ya no tiene saldo.");
            }
            BigDecimal saldoPrestamo = SaldosCuotas.total(cuotas);
            saldo = saldo.add(saldoPrestamo);
            EstadoPrestamos primera = cuotas.getFirst().cuota();
            origenes.add(ConvenioPrestamo.builder()
                    .prestamo(primera.getIdCalculadoraPrestamo())
                    .cliente(primera.getIdCliente())
                    .situacionAnterior(prestamo.situacion())
                    .cuotasConvenidas(cuotas.size())
                    .cuotasVencidas((int) cuotas.stream().filter(cuota -> cuota.cuota().vencida(hoy)).count())
                    .abonadoConvenido(cuotas.stream().map(CuotaConSaldo::abonado).reduce(Dinero.CERO, BigDecimal::add))
                    .saldoConvenido(saldoPrestamo)
                    .registradoPor(usuario)
                    .fechaRegistro(ahora)
                    .build());
            cuotas.forEach(cuota -> porConvenir.add(cuota.cuota()));
        }
        if (saldo.compareTo(Dinero.de(request.saldoEsperado())) != 0) {
            throw ApiException.conflicto("El saldo de los préstamos cambió (ahora " + Dinero.texto(saldo)
                    + "); puede que haya entrado un pago. Revisa el resumen y vuelve a generar.");
        }

        Plan plan = plan(saldo, request, condiciones);
        Registrado nuevo = registroPrestamo.registrar(titular, condiciones.modalidad(), plan, hoy, usuario);
        porConvenir.forEach(EstadoPrestamos::pasarAConvenio);

        Convenio convenio = Convenio.builder()
                .llaveIdempotencia(llave.valor())
                .hashPeticion(huella)
                .titular(titular)
                .prestamoConvenio(nuevo.prestamo())
                .modalidad(condiciones.modalidad())
                .numeroPagos(request.numeroPagos())
                .fechaPrimerPago(request.fechaPrimerPago())
                .saldoConvenido(saldo)
                .tasaInteres(plan.tasaMensual())
                .interes(plan.interes())
                .totalPagar(plan.total())
                .importeCuota(plan.importeCuota())
                .motivo(request.motivo().strip())
                .registradoPor(usuario)
                .fechaRegistro(ahora)
                .ipOrigen(ipOrigen)
                .build();
        origenes.forEach(convenio::agregar);
        convenioRepository.save(convenio);
        convenio.asignarFolio();

        log.info("Convenio {} generado por {} desde {}: titular {}, préstamos {}, saldo {}, interés {}, préstamo nuevo {}",
                convenio.getFolio(), usuario, ipOrigen, titular.getIdCliente(), condiciones.idsPrestamo(), saldo,
                plan.interes(), nuevo.prestamo().getIdCalculadoraPrestamo());
        return ResultadoOperacion.nueva(respuesta(convenio, nuevo.cuotas()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConvenioDetalleResponse cancelar(Integer idConvenio, CancelarConvenioRequest request, String usuario) {
        String motivo = request.motivo().strip();
        if (motivo.length() < MINIMO_MOTIVO) {
            throw ApiException.datosInvalidos("Revisa el motivo.", List.of(new CampoError("motivo",
                    "Escribe el motivo de la cancelación (10 a 250 caracteres).")));
        }
        Convenio convenio = convenioRepository.bloquear(idConvenio)
                .orElseThrow(() -> ApiException.noEncontrado(CONVENIO_NO_EXISTE));
        Integer idPrestamoNuevo = convenio.getPrestamoConvenio().getIdCalculadoraPrestamo();
        // Con las cuotas del préstamo nuevo bloqueadas, ningún cobro entra a la mitad.
        List<EstadoPrestamos> cuotasNuevas = cuotaRepository.bloquearDelPrestamo(idPrestamoNuevo);
        long pagos = pagoRepository.contarPagosVigentesDelPrestamo(idPrestamoNuevo);
        Optional<String> impedimento = ReglaCancelacionConvenio.loImpide(convenio.vigente(), pagos);
        if (impedimento.isPresent()) {
            throw ApiException.conflicto(impedimento.get());
        }

        cuotasNuevas.forEach(EstadoPrestamos::cancelar);
        int regresadas = 0;
        List<Integer> origenes = convenio.getPrestamos().stream()
                .map(origen -> origen.getPrestamo().getIdCalculadoraPrestamo())
                .sorted(Comparator.naturalOrder())
                .toList();
        for (Integer idOrigen : origenes) {
            List<EstadoPrestamos> convenidas = cuotaRepository.bloquearDelPrestamoConEstado(idOrigen,
                    EstadoCuota.EN_CONVENIO.codigo());
            for (CuotaConSaldo cuota : saldosCuotas.calcular(convenidas)) {
                cuota.cuota().regresarDeConvenio(cuota.abonado());
                regresadas++;
            }
        }
        convenio.cancelar(usuario, clock.instant(), motivo);
        // Las consultas del detalle son SQL: primero se escriben los cambios de esta transacción.
        convenioRepository.flush();

        log.info("Convenio {} cancelado por {}: préstamo {} cancelado, {} cuotas regresaron a {} préstamos: {}",
                convenio.getFolio(), usuario, idPrestamoNuevo, regresadas, origenes.size(), motivo);
        return consultaService.detalle(idConvenio);
    }

    /** Validaciones del plan que dependen de la base o de hoy (las de formato van en el request). */
    private Condiciones condiciones(PlanConvenio plan, LocalDate hoy) {
        Modalidad modalidad = modalidadRepository.findById(plan.idModalidad())
                .orElseThrow(() -> invalido("idModalidad", "La periodicidad no existe."));
        Periodicidad periodicidad = Periodicidad.deDias(modalidad.getDiasActivos())
                .orElseThrow(() -> invalido("idModalidad", "Esta periodicidad no está disponible para convenios."));
        if (plan.fechaPrimerPago().isBefore(hoy)) {
            throw invalido("fechaPrimerPago", "La fecha del primer pago no puede ser anterior a hoy.");
        }
        // Sin repetidos y en el orden en que llegaron.
        List<Integer> ids = List.copyOf(new LinkedHashSet<>(plan.idsPrestamo()));
        return new Condiciones(ids, modalidad, periodicidad);
    }

    /** Los préstamos existen y tienen saldo: al corriente o con atraso (no liquidados ni ya en convenio). */
    private List<Prestamo> convenibles(List<Integer> ids, LocalDate hoy) {
        return ids.stream()
                .map(id -> {
                    Prestamo prestamo = consultaRepository.buscarPorId(id, hoy)
                            .orElseThrow(() -> ApiException.noEncontrado("El préstamo #" + id + " no existe."));
                    if (prestamo.situacion() == SituacionPrestamo.EN_CONVENIO) {
                        throw ApiException.conflicto("El préstamo #" + id + " ya está en el convenio "
                                + prestamo.convenioDestino() + ".");
                    }
                    if (prestamo.situacion() == SituacionPrestamo.LIQUIDADO) {
                        throw ApiException.conflicto("El préstamo #" + id + " ya está liquidado.");
                    }
                    if (prestamo.situacion() == SituacionPrestamo.CANCELADO) {
                        throw ApiException.conflicto("El préstamo #" + id + " está cancelado.");
                    }
                    return prestamo;
                })
                .toList();
    }

    private static Plan plan(BigDecimal saldo, PlanConvenio plan, Condiciones condiciones) {
        return CalculadoraPrestamo.calcular(saldo, plan.tasaInteres(), plan.numeroPagos(), condiciones.periodicidad(),
                plan.fechaPrimerPago());
    }

    private static ConvenioResponse respuesta(Convenio convenio, List<EstadoPrestamos> calendario) {
        int pagosPorMes = Periodicidad.deDias(convenio.getModalidad().getDiasActivos())
                .map(Periodicidad::pagosPorMes)
                .orElse(1);
        return ConvenioMapper.convenio(convenio, calendario,
                ConvenioMapper.meses(convenio.getNumeroPagos(), pagosPorMes));
    }

    private static ApiException invalido(String campo, String mensaje) {
        return ApiException.datosInvalidos(SimularConvenioRequest.MENSAJE, List.of(new CampoError(campo, mensaje)));
    }

    private record Condiciones(List<Integer> idsPrestamo, Modalidad modalidad, Periodicidad periodicidad) {
    }
}
