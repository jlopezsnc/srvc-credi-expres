package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.view.response.ReporteCarteraResponse;
import com.crediexpress.service.ReporteService;
import com.crediexpress.service.reporte.PeriodoReporte;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReporteController.class)
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class ReporteControllerTest {

    private static final String BASE = "/operaciones-reportes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReporteService reporteService;

    @Test
    void laCarteraSaleConElDineroComoTexto() throws Exception {
        BigDecimal mil = new BigDecimal("1000.00");
        when(reporteService.cartera()).thenReturn(new ReporteCarteraResponse(LocalDate.of(2026, 10, 30),
                new ReporteCarteraResponse.Totales(1, 1, 0, 0, mil, new BigDecimal("0.00"), mil,
                        new BigDecimal("0.0")),
                List.of(), List.of()));

        mockMvc.perform(get(BASE + "/cartera").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Reporte de cartera."))
                .andExpect(jsonPath("$.objetoResponse.fecha").value("2026-10-30"))
                .andExpect(jsonPath("$.objetoResponse.totales.saldoTotal").value("1000.00"))
                .andExpect(jsonPath("$.objetoResponse.totales.porcentajeVencido").value("0.0"));
    }

    @Test
    void pasaElPeriodoAlServicio() throws Exception {
        mockMvc.perform(get(BASE + "/cobranza").param("desde", "2026-10-01").param("hasta", "2026-10-30")
                        .with(jwt()))
                .andExpect(status().isOk());

        verify(reporteService).cobranza(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 30)));
    }

    @Test
    void unPeriodoInvalidoResponde400ConElCampo() throws Exception {
        when(reporteService.cobranza(any(), any()))
                .thenAnswer(invocacion -> new PeriodoReporte(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 1)));

        mockMvc.perform(get(BASE + "/cobranza").param("desde", "2026-10-30").param("hasta", "2026-10-01")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(PeriodoReporte.MENSAJE))
                .andExpect(jsonPath("$.campos[0].campo").value("desde"));
    }

    @Test
    void unaFechaMalFormadaResponde400() throws Exception {
        mockMvc.perform(get(BASE + "/cobranza").param("desde", "30/10/2026").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("desde"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("Usa el formato aaaa-mm-dd."));

        verifyNoInteractions(reporteService);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(BASE + "/cobranza")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/cartera")).andExpect(status().isUnauthorized());

        verifyNoInteractions(reporteService);
    }
}
