package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.exception.ApiException;
import com.crediexpress.service.ClienteService;
import com.crediexpress.service.EstadoPrestamosService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Todos los errores salen con el sobre común, el código HTTP real y sin detalles internos. */
@WebMvcTest(controllers = {
        ClienteController.class,
        EstadoPrestamosController.class,
        ApiExceptionHandlerTest.ValidacionController.class})
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, ApiExceptionHandlerTest.ValidacionController.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class ApiExceptionHandlerTest {

    private static final String LISTAR_CLIENTES = "/operaciones-cliente/cliente-lst";
    private static final String AGREGAR_CLIENTE = "/operaciones-cliente/cliente-agregar";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService clienteService;

    @MockitoBean
    private EstadoPrestamosService estadoPrestamosService;

    @Test
    void errorDeNegocioSaleConSuCodigoYMensaje() throws Exception {
        when(clienteService.listarCliente()).thenThrow(ApiException.conflicto("La cuota ya está pagada."));

        mockMvc.perform(get(LISTAR_CLIENTES).with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.mensaje").value("La cuota ya está pagada."))
                .andExpect(jsonPath("$.objetoResponse").doesNotExist())
                .andExpect(jsonPath("$.campos").doesNotExist());
    }

    @Test
    void errorInesperadoNoExponeDetallesInternos() throws Exception {
        when(clienteService.listarCliente())
                .thenThrow(new IllegalStateException("SQL: select * from cliente — detalle interno"));

        mockMvc.perform(get(LISTAR_CLIENTES).with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.httpStatus").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.mensaje").value(ApiExceptionHandler.MENSAJE_INESPERADO))
                .andExpect(content().string(not(containsString("detalle interno"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void cuerpoMalFormadoResponde400() throws Exception {
        mockMvc.perform(post(AGREGAR_CLIENTE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{ no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.mensaje").value(ApiExceptionHandler.MENSAJE_CUERPO_INVALIDO));
    }

    @Test
    void parametroDeTipoIncorrectoResponde400() throws Exception {
        mockMvc.perform(get("/operaciones-prestamos/buscar-ep/abc").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(ApiExceptionHandler.MENSAJE_PARAMETROS));
    }

    @Test
    void rutaInexistenteResponde404ConElSobre() throws Exception {
        mockMvc.perform(get("/operaciones-cliente/no-existe").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"));
    }

    @Test
    void validacionDevuelveLosCamposConSuMensaje() throws Exception {
        mockMvc.perform(post("/prueba-validacion").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importe\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(ApiExceptionHandler.MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[?(@.campo == 'importe')].mensaje")
                        .value("El importe debe ser mayor a cero."))
                .andExpect(jsonPath("$.campos[?(@.campo == 'idCuota')].mensaje")
                        .value("Selecciona la cuota."));
    }

    /** Endpoint solo para probar las validaciones con Bean Validation. */
    @RestController
    static class ValidacionController {

        record Peticion(
                @NotNull(message = "Selecciona la cuota.") Integer idCuota,
                @DecimalMin(value = "0.01", message = "El importe debe ser mayor a cero.") BigDecimal importe) {
        }

        @PostMapping("/prueba-validacion")
        String validar(@Valid @RequestBody Peticion peticion) {
            return "ok";
        }
    }
}
