package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstatusCliente;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.ModalidadRepository;
import com.crediexpress.dto.view.request.CalculadoraPagosRequest;
import com.crediexpress.dto.view.response.CalcularPagoResponse;
import com.crediexpress.dto.view.response.CampoError;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.dto.view.response.EstadoPrestamosResponse;
import com.crediexpress.dto.view.response.ModalidadResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.CalculadoraPrestamo;
import com.crediexpress.service.CalculadoraPrestamo.Periodicidad;
import com.crediexpress.service.CalculadoraPrestamo.Plan;
import com.crediexpress.service.EstadoPrestamosService;
import com.crediexpress.service.prestamo.RegistroPrestamo;
import com.crediexpress.service.prestamo.RegistroPrestamo.Registrado;
import com.crediexpress.util.Dinero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EstadoPrestamosServiceImpl implements EstadoPrestamosService {

    static final String MENSAJE_GENERADO = "Prestamo generado correctamente";

    private final EstadoPrestamosRepository estadoPrestamosRepository;
    private final ModalidadRepository modalidadRepository;
    private final ClienteRepository clienteRepository;
    private final RegistroPrestamo registroPrestamo;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<EstadoPrestamosResponse> getEstadosPrestamos() {
        return estadoPrestamosRepository.findAll().stream()
                .map(EstadoPrestamosServiceImpl::getResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoPrestamosResponse getEstadoPrestamosById(Integer id) {
        return estadoPrestamosRepository.findById(id)
                .map(EstadoPrestamosServiceImpl::getResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstadoPrestamosResponse> getEstadoPrestamosByIdCliente(Integer id) {
        return estadoPrestamosRepository.findByIdCliente(id).stream()
                .map(EstadoPrestamosServiceImpl::getResponse)
                .toList();
    }

    @Override
    @Transactional
    public CalcularPagoResponse guardarEstadoPrestamos(CalculadoraPagosRequest request, String usuario) {
        LocalDate hoy = LocalDate.now(clock);
        Cliente cliente = clienteRepository.findById(request.idCliente())
                .orElseThrow(() -> ApiException.noEncontrado("El cliente no existe."));
        if (cliente.getEstatusCliente() == EstatusCliente.INACTIVO) {
            throw ApiException.conflicto("El cliente está dado de baja. Reactívalo para darle un préstamo nuevo.");
        }
        Modalidad modalidad = modalidadRepository.findById(request.idModalidad())
                .orElseThrow(() -> invalido("idModalidad", "La periodicidad no existe."));
        Periodicidad periodicidad = Periodicidad.deDias(modalidad.getDiasActivos())
                .orElseThrow(() -> invalido("idModalidad", "Esta periodicidad no está disponible para préstamos."));
        if (!periodicidad.permite(request.numeroCuota())) {
            throw invalido("numeroCuota", "Para pagos " + modalidad.getDescripcion().toLowerCase()
                    + "es elige " + plazosTexto(periodicidad) + " pagos.");
        }
        if (request.fechaInicioCredito().isBefore(hoy)) {
            throw invalido("fechaInicioCredito", "La fecha del primer pago no puede ser anterior a hoy.");
        }

        Plan plan = CalculadoraPrestamo.calcular(request.importeCredito(), request.tasaInteres(),
                request.numeroCuota(), periodicidad, request.fechaInicioCredito());

        Registrado registrado = registroPrestamo.registrar(cliente, modalidad, plan, hoy, usuario);
        CalculadoraPrestamos prestamo = registrado.prestamo();
        List<EstadoPrestamos> cuotas = registrado.cuotas();

        log.info("Préstamo {} generado: cliente {}, {} cuotas, total {}", prestamo.getIdCalculadoraPrestamo(),
                cliente.getIdCliente(), cuotas.size(), plan.total());

        return CalcularPagoResponse.builder()
                .plazosGenerados(cuotas.size())
                .resultado(MENSAJE_GENERADO)
                .idPrestamo(prestamo.getIdCalculadoraPrestamo())
                .interesGenerado(plan.interes())
                .totalPagar(plan.total())
                .importeCuota(plan.importeCuota())
                .importeUltimaCuota(plan.cuotas().getLast().importe())
                .build();
    }

    private static ApiException invalido(String campo, String mensaje) {
        return ApiException.datosInvalidos("Revisa los datos del préstamo.", List.of(new CampoError(campo, mensaje)));
    }

    /** [8, 10, 12, 14] → "8, 10, 12 o 14". */
    private static String plazosTexto(Periodicidad periodicidad) {
        List<String> plazos = periodicidad.plazosPermitidos().stream().map(String::valueOf).toList();
        if (plazos.size() == 1) {
            return plazos.getFirst();
        }
        return plazos.subList(0, plazos.size() - 1).stream().collect(Collectors.joining(", "))
                + " o " + plazos.getLast();
    }

    private static EstadoPrestamosResponse getResponse(EstadoPrestamos estadoPrestamos) {
        CalculadoraPrestamos prestamo = estadoPrestamos.getIdCalculadoraPrestamo();
        return EstadoPrestamosResponse.builder()
                .id(estadoPrestamos.getId())
                .modalidadResponse(ModalidadResponse.builder()
                        .idModalidad(prestamo.getIdModalidad().getIdModalidad())
                        .descripcion(prestamo.getIdModalidad().getDescripcion())
                        .build())
                .clienteResponse(ClienteResponse.builder()
                        .idCliente(estadoPrestamos.getIdCliente().getIdCliente())
                        .nombreCompleto(estadoPrestamos.getIdCliente().getNombreCompleto())
                        .build())
                .idCalculadoraPrestamo(prestamo.getIdCalculadoraPrestamo())
                .diasMoratorios(estadoPrestamos.getDiasMoratorios())
                .estadoPago(estadoPrestamos.getEstadoPago())
                .numeroCuota(estadoPrestamos.getNumeroCuota())
                .alerta(estadoPrestamos.getAlerta())
                .fechaPago(estadoPrestamos.getFechaPago())
                .fechaVencimiento(estadoPrestamos.getFechaVencimiento())
                .importeCuota(Dinero.de(estadoPrestamos.getImporteCuota()))
                .build();
    }
}
