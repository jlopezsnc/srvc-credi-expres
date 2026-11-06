package com.crediexpress.config.security;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.controller.ClienteController;
import com.crediexpress.controller.EstadoPrestamosController;
import com.crediexpress.dto.view.response.CalcularPagoResponse;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.service.ClienteService;
import com.crediexpress.service.EstadoPrestamosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La clase main no está en un paquete padre de este test (docs §2.1), por eso se indica explícitamente.
@WebMvcTest(controllers = {ClienteController.class, EstadoPrestamosController.class})
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class SeguridadEndpointsTest {

    private static final String LISTAR_CLIENTES = "/operaciones-cliente/cliente-lst";
    private static final String AGREGAR_CLIENTE = "/operaciones-cliente/cliente-agregar";
    private static final String GENERAR_PRESTAMO = "/operaciones-prestamos/calcular-pagos";
    private static final String CLIENTE_JSON = "{\"nombreCompleto\":\"Cliente Prueba\"}";
    // Mismo cuerpo que envía la pantalla "Nuevo préstamo" (los montos calculados se ignoran).
    private static final String PRESTAMO_JSON = """
            {"idCliente":"1","idModalidad":"2","numeroCuota":"8","importeCredito":"6000.00",
             "tasaInteres":"12","fechaInicioCredito":"2099-01-15","totalPagar":"8880.00"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService clienteService;

    @MockitoBean
    private EstadoPrestamosService estadoPrestamosService;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(LISTAR_CLIENTES)).andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenNoPuedeAgregarClientes() throws Exception {
        mockMvc.perform(post(AGREGAR_CLIENTE).contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clienteService);
    }

    @Test
    void sinTokenNoPuedeGenerarPrestamos() throws Exception {
        mockMvc.perform(post(GENERAR_PRESTAMO).contentType(MediaType.APPLICATION_JSON).content(PRESTAMO_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(estadoPrestamosService);
    }

    @Test
    void tokenMalformadoResponde401() throws Exception {
        mockMvc.perform(get(LISTAR_CLIENTES).header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clienteService);
    }

    @Test
    void tokenValidoDeUsuarioFueraDeLaListaResponde403() throws Exception {
        mockMvc.perform(get(LISTAR_CLIENTES).with(jwt().jwt(j -> j.subject("uid-ajeno"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(AGREGAR_CLIENTE).with(jwt().jwt(j -> j.subject("uid-ajeno")))
                        .contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clienteService);
    }

    @Test
    void conTokenPuedeConsultarClientes() throws Exception {
        when(clienteService.listarCliente()).thenReturn(List.of(ClienteResponse.builder().idCliente(1).build()));

        mockMvc.perform(get(LISTAR_CLIENTES).with(jwt())).andExpect(status().isOk());
    }

    @Test
    void conTokenPuedeAgregarClientes() throws Exception {
        when(clienteService.validarRequest(any())).thenReturn(false);
        when(clienteService.guardarCliente(any(), any())).thenReturn(ClienteResponse.builder().idCliente(1).build());

        mockMvc.perform(post(AGREGAR_CLIENTE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void conTokenPuedeGenerarPrestamos() throws Exception {
        when(estadoPrestamosService.guardarEstadoPrestamos(any(), any()))
                .thenReturn(CalcularPagoResponse.builder().plazosGenerados(8).resultado("OK").build());

        mockMvc.perform(post(GENERAR_PRESTAMO).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(PRESTAMO_JSON))
                .andExpect(status().isOk());
    }
}
