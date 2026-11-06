package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.entity.EstatusMovimientoCaja;
import com.crediexpress.dto.entity.TipoMovimientoCaja;
import com.crediexpress.dto.view.request.RegistrarMovimientoCajaRequest;
import com.crediexpress.dto.view.response.MovimientoCajaResponse;
import com.crediexpress.dto.view.response.SaldoCajaResponse;
import com.crediexpress.service.CajaService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CajaController.class)
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class CajaControllerTest {

    private static final String BASE = "/operaciones-caja";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CajaService cajaService;

    @Test
    void elSaldoSaleConElDineroComoTexto() throws Exception {
        BigDecimal cero = new BigDecimal("0.00");
        BigDecimal mil = new BigDecimal("1000.00");
        when(cajaService.saldo(2026)).thenReturn(new SaldoCajaResponse(2026, List.of(2026),
                new SaldoCajaResponse.Totales(mil, cero, cero, cero, mil), cero, mil, mil,
                List.of(new SaldoCajaResponse.Mes("2026-10", mil, cero, cero, cero, mil, mil))));

        mockMvc.perform(get(BASE + "/saldo").param("anio", "2026").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Saldo de caja."))
                .andExpect(jsonPath("$.objetoResponse.saldoActual").value("1000.00"))
                .andExpect(jsonPath("$.objetoResponse.meses[0].mes").value("2026-10"))
                .andExpect(jsonPath("$.objetoResponse.meses[0].acumulado").value("1000.00"));
    }

    @Test
    void registraConElUsuarioDelTokenY201() throws Exception {
        when(cajaService.registrar(any(), eq("user"))).thenReturn(new MovimientoCajaResponse(1,
                TipoMovimientoCaja.APORTACION, new BigDecimal("50000.00"), LocalDate.of(2026, 11, 5), "Capital inicial",
                EstatusMovimientoCaja.VIGENTE, "user", Instant.parse("2026-11-05T18:00:00Z"), null, null, null));

        mockMvc.perform(post(BASE + "/movimiento-registrar").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"APORTACION","importe":"50000","fecha":"2026-11-05","concepto":"Capital inicial"}
                                """)
                        .with(jwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Movimiento registrado."))
                .andExpect(jsonPath("$.objetoResponse.importe").value("50000.00"));

        verify(cajaService).registrar(eq(new RegistrarMovimientoCajaRequest(TipoMovimientoCaja.APORTACION,
                new BigDecimal("50000"), LocalDate.of(2026, 11, 5), "Capital inicial")), eq("user"));
    }

    @Test
    void unMovimientoIncompletoEs400SinLlegarAlServicio() throws Exception {
        mockMvc.perform(post(BASE + "/movimiento-registrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RETIRO\",\"importe\":\"0\",\"concepto\":\"\"}").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(RegistrarMovimientoCajaRequest.MENSAJE));

        mockMvc.perform(put(BASE + "/movimiento-cancelar/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}").with(jwt()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cajaService);
    }

    @Test
    void sinTokenEs401() throws Exception {
        mockMvc.perform(get(BASE + "/saldo")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cajaService);
    }
}
