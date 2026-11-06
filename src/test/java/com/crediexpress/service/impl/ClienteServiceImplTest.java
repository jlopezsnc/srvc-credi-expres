package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstatusCliente;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.repository.EstadoPrestamosRepository;
import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.request.ClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private EstadoPrestamosRepository estadoPrestamosRepository;

    private static final Instant AHORA = Instant.parse("2026-11-05T18:30:00Z");
    private static final String USUARIO = "ana@negocio.mx";

    @Spy
    private Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);

    @InjectMocks
    private ClienteServiceImpl servicio;

    private static ActualizarClienteRequest request(String curp) {
        return new ActualizarClienteRequest("  JUAN CORDOVA CORDOVA ", curp, "9171007543", "",
                " CALLE PRINCIPAL ", "", "HUIMANGUILLO");
    }

    private static Cliente clienteMigrado() {
        // Cliente cargado del Excel: sin CURP ni teléfono.
        return Cliente.builder().idCliente(7).nombreCompleto("JUAN CORDOVA").domicilioCiudad("HUIMANGUILLO")
                .correoElectronico("viejo@ejemplo.mx").build();
    }

    @Test
    void reemplazaLosDatosNormalizaLaCurpYGuardaVaciosComoNull() {
        Cliente cliente = clienteMigrado();
        when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));
        when(clienteRepository.existsByCurpAndIdClienteNot("CORJ101020MTCPRR06", 7)).thenReturn(false);
        when(clienteRepository.saveAndFlush(cliente)).thenReturn(cliente);

        ClienteResponse respuesta = servicio.actualizarCliente(7, request("corj101020mtcprr06 "), USUARIO);

        assertThat(respuesta.getIdCliente()).isEqualTo(7);
        assertThat(respuesta.getNombreCompleto()).isEqualTo("JUAN CORDOVA CORDOVA");
        assertThat(respuesta.getCurp()).isEqualTo("CORJ101020MTCPRR06");
        assertThat(respuesta.getTelefono()).isEqualTo("9171007543");
        assertThat(respuesta.getCorreoElectronico()).isNull();
        assertThat(respuesta.getDomicilioCalle()).isEqualTo("CALLE PRINCIPAL");
        assertThat(respuesta.getDomicilioColonia()).isNull();
        assertThat(cliente.getActualizadoPor()).isEqualTo(USUARIO);
        assertThat(cliente.getFechaActualizacion()).isEqualTo(AHORA);
    }

    @Test
    void elAltaGuardaQuienLoRegistro() {
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        ClienteRequest alta = new ClienteRequest();
        alta.setNombreCompleto("CLIENTE NUEVO");

        servicio.guardarCliente(alta, USUARIO);

        verify(clienteRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                c -> USUARIO.equals(c.getRegistradoPor()) && c.getActualizadoPor() == null));
    }

    @Test
    void clienteInexistenteResponde404() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizarCliente(99, request("CORJ101020MTCPRR06"), USUARIO))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void curpDeOtroClienteResponde409SinGuardar() {
        when(clienteRepository.findById(7)).thenReturn(Optional.of(clienteMigrado()));
        when(clienteRepository.existsByCurpAndIdClienteNot("CORJ101020MTCPRR06", 7)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizarCliente(7, request("CORJ101020MTCPRR06"), USUARIO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Ya existe otro cliente registrado con esta CURP.");
                });
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void bajaSinPrestamosActivosLoDejaInactivoYGuardaQuienLoHizo() {
        Cliente cliente = clienteMigrado();
        when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));
        when(estadoPrestamosRepository.existsConSaldoDelCliente(7, EstadoCuota.codigosConSaldo())).thenReturn(false);
        when(clienteRepository.saveAndFlush(cliente)).thenReturn(cliente);

        ClienteResponse respuesta = servicio.darDeBaja(7, USUARIO);

        assertThat(respuesta.getEstatusCliente()).isEqualTo("INACTIVO");
        assertThat(cliente.getEstatusCliente()).isEqualTo(EstatusCliente.INACTIVO);
        assertThat(cliente.getActualizadoPor()).isEqualTo(USUARIO);
        assertThat(cliente.getFechaActualizacion()).isEqualTo(AHORA);
    }

    @Test
    void bajaConPrestamosActivosResponde409YNoCambiaNada() {
        Cliente cliente = clienteMigrado();
        when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));
        when(estadoPrestamosRepository.existsConSaldoDelCliente(7, EstadoCuota.codigosConSaldo())).thenReturn(true);

        assertThatThrownBy(() -> servicio.darDeBaja(7, USUARIO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(
                            "El cliente tiene préstamos activos. Liquídalos o cancélalos antes de darlo de baja.");
                });
        assertThat(cliente.getEstatusCliente()).isEqualTo(EstatusCliente.ACTIVO);
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void bajaDeUnClienteYaInactivoNoCambiaNada() {
        Cliente cliente = clienteMigrado();
        cliente.setEstatusCliente(EstatusCliente.INACTIVO);
        when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));

        assertThat(servicio.darDeBaja(7, USUARIO).getEstatusCliente()).isEqualTo("INACTIVO");
        verify(clienteRepository, never()).saveAndFlush(any());
        assertThat(cliente.getActualizadoPor()).isNull();
    }

    @Test
    void reactivarLoDejaActivo() {
        Cliente cliente = clienteMigrado();
        cliente.setEstatusCliente(EstatusCliente.INACTIVO);
        when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));
        when(clienteRepository.saveAndFlush(cliente)).thenReturn(cliente);

        assertThat(servicio.reactivar(7, USUARIO).getEstatusCliente()).isEqualTo("ACTIVO");
        assertThat(cliente.getActualizadoPor()).isEqualTo(USUARIO);
    }

    @Test
    void bajaOReactivarDeUnClienteInexistenteResponde404() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.darDeBaja(99, USUARIO)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> servicio.reactivar(99, USUARIO)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void elAltaQuedaActiva() {
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        ClienteRequest alta = new ClienteRequest();
        alta.setNombreCompleto("CLIENTE NUEVO");

        assertThat(servicio.guardarCliente(alta, USUARIO).getEstatusCliente()).isEqualTo("ACTIVO");
    }
}
