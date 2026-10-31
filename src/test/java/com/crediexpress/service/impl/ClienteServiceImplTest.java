package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.repository.ClienteRepository;
import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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

        ClienteResponse respuesta = servicio.actualizarCliente(7, request("corj101020mtcprr06 "));

        assertThat(respuesta.getIdCliente()).isEqualTo(7);
        assertThat(respuesta.getNombreCompleto()).isEqualTo("JUAN CORDOVA CORDOVA");
        assertThat(respuesta.getCurp()).isEqualTo("CORJ101020MTCPRR06");
        assertThat(respuesta.getTelefono()).isEqualTo("9171007543");
        assertThat(respuesta.getCorreoElectronico()).isNull();
        assertThat(respuesta.getDomicilioCalle()).isEqualTo("CALLE PRINCIPAL");
        assertThat(respuesta.getDomicilioColonia()).isNull();
    }

    @Test
    void clienteInexistenteResponde404() {
        when(clienteRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizarCliente(99, request("CORJ101020MTCPRR06")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void curpDeOtroClienteResponde409SinGuardar() {
        when(clienteRepository.findById(7)).thenReturn(Optional.of(clienteMigrado()));
        when(clienteRepository.existsByCurpAndIdClienteNot("CORJ101020MTCPRR06", 7)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizarCliente(7, request("CORJ101020MTCPRR06")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Ya existe otro cliente registrado con esta CURP.");
                });
        verify(clienteRepository, never()).saveAndFlush(any());
    }
}
