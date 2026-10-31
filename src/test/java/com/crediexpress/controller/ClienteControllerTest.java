package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.view.request.ActualizarClienteRequest;
import com.crediexpress.dto.view.response.ClienteResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.ClienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClienteController.class)
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class ClienteControllerTest {

    private static final String URL = "/operaciones-cliente/cliente-actualizar/7";
    private static final String CUERPO = """
            {"nombreCompleto":"JUAN CORDOVA CORDOVA","curp":"CORJ101020MTCPRR06","telefono":"9171007543",
             "correoElectronico":"","domicilioCalle":"CALLE PRINCIPAL","domicilioColonia":"COLONIA PERIFERICO",
             "domicilioCiudad":"HUIMANGUILLO"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService clienteService;

    @Test
    void actualizaYRespondeElCliente() throws Exception {
        when(clienteService.actualizarCliente(eq(7), any(ActualizarClienteRequest.class))).thenReturn(
                ClienteResponse.builder().idCliente(7).nombreCompleto("JUAN CORDOVA CORDOVA")
                        .curp("CORJ101020MTCPRR06").build());

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(CUERPO).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.mensaje").value("Cliente actualizado."))
                .andExpect(jsonPath("$.objetoResponse.idCliente").value(7))
                .andExpect(jsonPath("$.objetoResponse.curp").value("CORJ101020MTCPRR06"));
    }

    @Test
    void datosInvalidosResponden400PorCampoSinLlegarAlServicio() throws Exception {
        String cuerpo = """
                {"nombreCompleto":"  ","curp":"CORJ101020MTCPRR01","telefono":"993 123",
                 "correoElectronico":"no-es-correo","domicilioCalle":"","domicilioColonia":"","domicilioCiudad":""}
                """;

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos del cliente."))
                .andExpect(jsonPath("$.campos[*].campo").value(
                        containsInAnyOrder("nombreCompleto", "curp", "telefono", "correoElectronico")));
        verifyNoInteractions(clienteService);
    }

    @Test
    void curpVaciaEsObligatoria() throws Exception {
        String cuerpo = CUERPO.replace("CORJ101020MTCPRR06", "");

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("curp"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("Captura la CURP."));
    }

    @Test
    void curpDeOtroClienteResponde409() throws Exception {
        when(clienteService.actualizarCliente(eq(7), any(ActualizarClienteRequest.class)))
                .thenThrow(ApiException.conflicto("Ya existe otro cliente registrado con esta CURP."));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(CUERPO).with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe otro cliente registrado con esta CURP."));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(clienteService);
    }
}
