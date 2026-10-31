package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import com.crediexpress.dto.view.response.ClienteResumenResponse;
import com.crediexpress.dto.view.response.ModalidadResumenResponse;
import com.crediexpress.dto.view.response.PaginaPrestamosResponse;
import com.crediexpress.dto.view.response.PrestamoResumenResponse;
import com.crediexpress.service.PrestamoConsultaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PrestamoConsultaController.class)
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class PrestamoConsultaControllerTest {

    private static final String BASE = "/operaciones-prestamos/prestamos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrestamoConsultaService consultaService;

    @Test
    void porDefectoPideActivosPorProximoPagoDeDiezEnDiez() throws Exception {
        PrestamoResumenResponse prestamo = new PrestamoResumenResponse(12,
                new ClienteResumenResponse(2, "CLIENTE DE PRUEBA", null), new ModalidadResumenResponse(2, "Quincenal"),
                8, 3, 1, new BigDecimal("1110.00"), new BigDecimal("8880.00"), new BigDecimal("5150.00"),
                LocalDate.of(2026, 10, 25), 4, SituacionPrestamo.CON_ATRASO, null, null);
        when(consultaService.listar(FiltroPrestamos.ACTIVOS, null, null, OrdenPrestamos.PROXIMO_PAGO, Direccion.ASC,
                0, 10))
                .thenReturn(PaginaPrestamosResponse.de(List.of(prestamo), 0, 10, 1,
                        new PaginaPrestamosResponse.Contadores(1, 0, 1, 0, 0, 0, 1)));

        mockMvc.perform(get(BASE).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("1 registros encontrados."))
                .andExpect(jsonPath("$.objetoResponse.contenido[0].estado").value("CON_ATRASO"))
                .andExpect(jsonPath("$.objetoResponse.contenido[0].cuotasPagadas").value(3))
                .andExpect(jsonPath("$.objetoResponse.contenido[0].proximoPago").value("2026-10-25"))
                .andExpect(jsonPath("$.objetoResponse.contenido[0].saldoPendiente").value("5150.00"))
                .andExpect(jsonPath("$.objetoResponse.contadores.conAtraso").value(1));
    }

    @Test
    void pasaFiltroOrdenYPagina() throws Exception {
        when(consultaService.listar(FiltroPrestamos.LIQUIDADOS, "ana", 7, OrdenPrestamos.SALDO, Direccion.DESC, 2, 10))
                .thenReturn(PaginaPrestamosResponse.de(List.of(), 2, 10, 0,
                        new PaginaPrestamosResponse.Contadores(0, 0, 0, 0, 0, 0, 0)));

        mockMvc.perform(get(BASE).param("filtro", "LIQUIDADOS").param("busqueda", "ana").param("idCliente", "7")
                        .param("orden", "SALDO").param("direccion", "DESC").param("pagina", "2").param("tamano", "10")
                        .with(jwt()))
                .andExpect(status().isOk());

        verify(consultaService).listar(FiltroPrestamos.LIQUIDADOS, "ana", 7, OrdenPrestamos.SALDO, Direccion.DESC, 2, 10);
    }

    @Test
    void rechazaColumnasYTamanosFueraDeLaLista() throws Exception {
        mockMvc.perform(get(BASE).param("orden", "id_prestamo; DROP TABLE pago").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("orden"));
        mockMvc.perform(get(BASE).param("tamano", "500").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("tamano"));

        verifyNoInteractions(consultaService);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/12")).andExpect(status().isUnauthorized());

        verifyNoInteractions(consultaService);
    }
}
