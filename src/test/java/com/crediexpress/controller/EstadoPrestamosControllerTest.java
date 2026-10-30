package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.view.response.CalcularPagoResponse;
import com.crediexpress.service.ClienteService;
import com.crediexpress.service.EstadoPrestamosService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EstadoPrestamosController.class, ClienteController.class})
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class EstadoPrestamosControllerTest {

    private static final String GENERAR_PRESTAMO = "/operaciones-prestamos/calcular-pagos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EstadoPrestamosService estadoPrestamosService;

    @MockitoBean
    private ClienteService clienteService;

    @Test
    void aceptaElCuerpoQueEnviaLaPantallaYDevuelveLoQueCalculoElBack() throws Exception {
        when(estadoPrestamosService.guardarEstadoPrestamos(any())).thenReturn(CalcularPagoResponse.builder()
                .plazosGenerados(8)
                .resultado("Prestamo generado correctamente")
                .idPrestamo(77)
                .totalPagar(new BigDecimal("8880.00"))
                .importeCuota(new BigDecimal("1110.00"))
                .importeUltimaCuota(new BigDecimal("1110.00"))
                .interesGenerado(new BigDecimal("2880.00"))
                .build());

        // Valores como texto y campos de más, igual que el front de hoy.
        String cuerpo = """
                {"idCliente":"1","idModalidad":"2","numeroCuota":"8","importeCuota":"1110.00",
                 "fechaGeneracionOperacion":"2026-10-30","fechaInicioCredito":"2099-01-15",
                 "importeCredito":"6000.00","tasaInteres":"12","totalPagar":"1.00","interesGenerado":"0",
                 "estadoPago":"1","diasMoratorios":"0","alerta":"VIGENTE","idCalculadoraPrestamo":"1"}
                """;

        mockMvc.perform(post(GENERAR_PRESTAMO).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.mensaje").value("Prestamo generado correctamente"))
                .andExpect(jsonPath("$.objetoResponse.plazosGenerados").value(8))
                .andExpect(jsonPath("$.objetoResponse.idPrestamo").value(77))
                .andExpect(jsonPath("$.objetoResponse.totalPagar").value("8880.00"));
    }

    @Test
    void sinDatosObligatoriosResponde400ConLosCampos() throws Exception {
        mockMvc.perform(post(GENERAR_PRESTAMO).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCliente\":1,\"tasaInteres\":\"150\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.campos[?(@.campo == 'idModalidad')].mensaje").value("Elige la periodicidad."))
                .andExpect(jsonPath("$.campos[?(@.campo == 'numeroCuota')]").exists())
                .andExpect(jsonPath("$.campos[?(@.campo == 'importeCredito')]").exists())
                .andExpect(jsonPath("$.campos[?(@.campo == 'fechaInicioCredito')]").exists())
                .andExpect(jsonPath("$.campos[?(@.campo == 'tasaInteres')].mensaje")
                        .value("El interés no puede ser mayor a 100%."));

        verifyNoInteractions(estadoPrestamosService);
    }
}
