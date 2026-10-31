package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.repository.CalculadoraPrestamosRepository;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.repository.ModalidadRepository;
import com.crediexpress.dto.view.request.CalculadoraPagosRequest;
import com.crediexpress.service.prestamo.RegistroPrestamo;
import com.crediexpress.dto.view.response.CalcularPagoResponse;
import com.crediexpress.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstadoPrestamosServiceImplTest {

    private static final ZoneId MEXICO = ZoneId.of("America/Mexico_City");
    // 30 de octubre de 2026 a las 23:30 en México (ya es 31 en UTC): "hoy" debe ser el 30.
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-31T05:30:00Z"), MEXICO);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    @Mock
    private EstadoPrestamosRepository estadoPrestamosRepository;
    @Mock
    private ModalidadRepository modalidadRepository;
    @Mock
    private CalculadoraPrestamosRepository calculadoraPrestamosRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Captor
    private ArgumentCaptor<List<EstadoPrestamos>> cuotasGuardadas;
    @Captor
    private ArgumentCaptor<CalculadoraPrestamos> prestamoGuardado;

    private EstadoPrestamosServiceImpl servicio;

    private final Cliente cliente = Cliente.builder().idCliente(1).nombreCompleto("CLIENTE PRUEBA").build();
    private final Modalidad quincenal = Modalidad.builder().idModalidad(2).descripcion("Quincenal").diasActivos(15).build();

    @BeforeEach
    void preparar() {
        servicio = new EstadoPrestamosServiceImpl(estadoPrestamosRepository, modalidadRepository, clienteRepository,
                new RegistroPrestamo(calculadoraPrestamosRepository, estadoPrestamosRepository), RELOJ);
    }

    private static CalculadoraPagosRequest peticion(int cuotas, LocalDate primerPago) {
        return new CalculadoraPagosRequest(1, 2, cuotas, new BigDecimal("6000"), new BigDecimal("12"), primerPago);
    }

    private void conClienteYModalidad() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(modalidadRepository.findById(2)).thenReturn(Optional.of(quincenal));
    }

    @Test
    void calculaYGuardaElPrestamoConSusCuotas() {
        conClienteYModalidad();
        when(calculadoraPrestamosRepository.save(any())).thenAnswer(invocacion -> {
            CalculadoraPrestamos prestamo = invocacion.getArgument(0);
            prestamo.setIdCalculadoraPrestamo(77);
            return prestamo;
        });

        CalcularPagoResponse respuesta = servicio.guardarEstadoPrestamos(peticion(14, HOY.plusDays(15)));

        verify(calculadoraPrestamosRepository).save(prestamoGuardado.capture());
        CalculadoraPrestamos prestamo = prestamoGuardado.getValue();
        assertThat(prestamo.getTotalPagar()).isEqualTo(new BigDecimal("11040.00"));
        assertThat(prestamo.getInteresGenerado()).isEqualTo(new BigDecimal("5040.00"));
        assertThat(prestamo.getImporteCuota()).isEqualTo(new BigDecimal("788.57"));
        assertThat(prestamo.getFechaSolicitud()).isEqualTo(HOY);

        verify(estadoPrestamosRepository).saveAll(cuotasGuardadas.capture());
        List<EstadoPrestamos> cuotas = cuotasGuardadas.getValue();
        assertThat(cuotas).hasSize(14);
        assertThat(cuotas.getFirst().getImporteCuota()).isEqualTo(new BigDecimal("788.57"));
        assertThat(cuotas.getLast().getImporteCuota()).isEqualTo(new BigDecimal("788.59"));
        assertThat(cuotas.getFirst().getFechaVencimiento()).isEqualTo(HOY.plusDays(15));
        assertThat(cuotas.get(1).getFechaVencimiento()).isEqualTo(HOY.plusDays(30));
        assertThat(cuotas).allSatisfy(cuota -> {
            assertThat(cuota.getEstadoPago()).isEqualTo(EstadoCuota.PENDIENTE.codigo());
            assertThat(cuota.getDiasMoratorios()).isZero();
            assertThat(cuota.getIdCalculadoraPrestamo()).isSameAs(prestamo);
        });

        assertThat(respuesta.getIdPrestamo()).isEqualTo(77);
        assertThat(respuesta.getPlazosGenerados()).isEqualTo(14);
        assertThat(respuesta.getImporteUltimaCuota()).isEqualTo(new BigDecimal("788.59"));
    }

    @Test
    void rechazaUnNumeroDePagosQueNoEsDeLaPeriodicidad() {
        conClienteYModalidad();

        assertThatThrownBy(() -> servicio.guardarEstadoPrestamos(peticion(16, HOY)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCampos()).singleElement().satisfies(campo -> {
                        assertThat(campo.campo()).isEqualTo("numeroCuota");
                        assertThat(campo.mensaje()).isEqualTo("Para pagos quincenales elige 8, 10, 12 o 14 pagos.");
                    });
                });
        verify(estadoPrestamosRepository, never()).saveAll(any());
    }

    @Test
    void rechazaUnPrimerPagoAnteriorAHoy() {
        conClienteYModalidad();

        assertThatThrownBy(() -> servicio.guardarEstadoPrestamos(peticion(8, HOY.minusDays(1))))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCampos()).singleElement()
                                .satisfies(campo -> assertThat(campo.campo()).isEqualTo("fechaInicioCredito")));
    }

    @Test
    void aceptaQueElPrimerPagoSeaHoyEnMexico() {
        conClienteYModalidad();
        when(calculadoraPrestamosRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.guardarEstadoPrestamos(peticion(8, HOY));

        verify(estadoPrestamosRepository).saveAll(any());
    }

    @Test
    void clienteInexistenteResponde404() {
        when(clienteRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.guardarEstadoPrestamos(peticion(8, HOY)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void modalidadSinReglaDePlazosSeRechaza() {
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(modalidadRepository.findById(2)).thenReturn(Optional.of(
                Modalidad.builder().idModalidad(2).descripcion("Mensual").diasActivos(30).build()));

        assertThatThrownBy(() -> servicio.guardarEstadoPrestamos(peticion(8, HOY)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
