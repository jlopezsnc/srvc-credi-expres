package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.view.response.InicioResponse;
import com.crediexpress.service.InicioService;
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
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InicioController.class)
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class InicioControllerTest {

    private static final String RESUMEN = "/operaciones-inicio/resumen";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InicioService inicioService;

    private static InicioResponse inicio(YearMonth mes) {
        InicioResponse.ResumenMes resumen = new InicioResponse.ResumenMes(mes, mes.atDay(1), mes.atEndOfMonth(),
                new BigDecimal("109600.00"), new BigDecimal("109600.00"), new BigDecimal("129500.00"),
                new BigDecimal("121500.00"), new BigDecimal("90.2"), 11, new BigDecimal("79000.00"), 4, 3,
                new BigDecimal("26350.00"), List.of());
        return new InicioResponse(mes, true, resumen, resumen,
                new InicioResponse.Variaciones(new BigDecimal("-7.8"), null, null),
                new InicioResponse.Hoy(LocalDate.of(2026, 10, 30), BigDecimal.TEN, BigDecimal.ONE,
                        new BigDecimal("10.0"), 38, 8, null, List.of()));
    }

    @Test
    void sinMesPideElActualYDevuelveElMesComoAnoMes() throws Exception {
        when(inicioService.resumen(null)).thenReturn(inicio(YearMonth.of(2026, 10)));

        mockMvc.perform(get(RESUMEN).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetoResponse.mes").value("2026-10"))
                .andExpect(jsonPath("$.objetoResponse.resumen.cumplimiento").value("90.2"))
                .andExpect(jsonPath("$.objetoResponse.variaciones.cobrado").value("-7.8"))
                .andExpect(jsonPath("$.objetoResponse.variaciones.otorgados").isEmpty());
    }

    @Test
    void recibeElMesComoAnoMes() throws Exception {
        when(inicioService.resumen(YearMonth.of(2026, 8))).thenReturn(inicio(YearMonth.of(2026, 8)));

        mockMvc.perform(get(RESUMEN).param("mes", "2026-08").with(jwt())).andExpect(status().isOk());

        verify(inicioService).resumen(YearMonth.of(2026, 8));
    }

    @Test
    void unMesMalEscritoResponde400SinLlegarAlServicio() throws Exception {
        mockMvc.perform(get(RESUMEN).param("mes", "octubre").with(jwt())).andExpect(status().isBadRequest());

        verifyNoInteractions(inicioService);
    }
}
