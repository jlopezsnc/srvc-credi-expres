package com.crediexpress.service.prestamo;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.repository.CalculadoraPrestamosRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.service.CalculadoraPrestamo.Plan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Guarda un préstamo ya calculado: el encabezado y una cuota pendiente por cada pago del plan. Lo
 * usan "Nuevo préstamo" y "Nuevo convenio" (el convenio es un préstamo más para Cobranza).
 */
@Component
@RequiredArgsConstructor
public class RegistroPrestamo {

    private static final String ALERTA_INICIAL = "VIGENTE";

    private final CalculadoraPrestamosRepository prestamoRepository;
    private final EstadoPrestamosRepository cuotaRepository;

    public record Registrado(CalculadoraPrestamos prestamo, List<EstadoPrestamos> cuotas) {
    }

    /** @param usuario quién lo genera (correo o uid del token), para auditoría. */
    public Registrado registrar(Cliente cliente, Modalidad modalidad, Plan plan, LocalDate fechaSolicitud,
                                String usuario) {
        CalculadoraPrestamos prestamo = prestamoRepository.save(CalculadoraPrestamos.builder()
                .idCliente(cliente)
                .idModalidad(modalidad)
                .importeCredito(plan.monto())
                .tasaInteres(plan.tasaMensual())
                .numeroCuotas(plan.cuotas().size())
                .importeCuota(plan.importeCuota())
                .totalPagar(plan.total())
                .interesGenerado(plan.interes())
                .fechaSolicitud(fechaSolicitud)
                .fechaInicioCredito(plan.cuotas().getFirst().fechaVencimiento())
                .registradoPor(usuario)
                .build());

        List<EstadoPrestamos> cuotas = plan.cuotas().stream()
                .map(cuota -> EstadoPrestamos.builder()
                        .idCalculadoraPrestamo(prestamo)
                        .idCliente(cliente)
                        .numeroCuota(cuota.numero())
                        .fechaVencimiento(cuota.fechaVencimiento())
                        .importeCuota(cuota.importe())
                        .estadoPago(EstadoCuota.PENDIENTE.codigo())
                        .diasMoratorios(0)
                        .alerta(ALERTA_INICIAL)
                        .build())
                .toList();
        cuotaRepository.saveAll(cuotas);
        return new Registrado(prestamo, cuotas);
    }
}
