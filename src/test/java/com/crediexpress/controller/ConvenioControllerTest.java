package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.entity.EstatusConvenio;
import com.crediexpress.dto.entity.SituacionConvenio;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.view.request.CancelarConvenioRequest;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroConvenios;
import com.crediexpress.dto.view.request.OrdenConvenios;
import com.crediexpress.dto.view.response.ClienteResumenResponse;
import com.crediexpress.dto.view.response.ConvenioDetalleResponse;
import com.crediexpress.dto.view.response.ConvenioResponse;
import com.crediexpress.dto.view.response.ConvenioResumenResponse;
import com.crediexpress.dto.view.response.ModalidadResumenResponse;
import com.crediexpress.dto.view.response.PaginaConveniosResponse;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse.CuotaPlan;
import com.crediexpress.dto.view.response.SimulacionConvenioResponse.PrestamoConvenido;
import com.crediexpress.service.ConvenioConsultaService;
import com.crediexpress.service.ConvenioService;
import com.crediexpress.service.cobranza.LlaveIdempotencia;
import com.crediexpress.service.cobranza.ResultadoOperacion;
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

@WebMvcTest(controllers = ConvenioController.class)
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class ConvenioControllerTest {

    private static final String BASE = "/operaciones-convenios";
    private static final String LLAVE = "0b7c2f8e-3d4a-4c1b-9e5f-6a7b8c9d0e1f";
    private static final String CORREO = "cobrador@ejemplo.mx";

    private static final String SIMULAR_JSON = """
            {"idsPrestamo":[12,18],"idModalidad":1,"numeroPagos":12,"tasaInteres":0,"fechaPrimerPago":"2026-11-06"}
            """;
    private static final String GENERAR_JSON = """
            {"idClienteTitular":1,"idsPrestamo":[12,18],"idModalidad":1,"numeroPagos":12,"tasaInteres":0,
             "fechaPrimerPago":"2026-11-06","motivo":"Los préstamos de su hermana los paga ella.",
             "saldoEsperado":"9910.00"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConvenioService convenioService;

    @MockitoBean
    private ConvenioConsultaService consultaService;

    private static ConvenioResumenResponse resumen(SituacionConvenio estado, EstatusConvenio estatus) {
        return new ConvenioResumenResponse(124, "CV-000124", new ClienteResumenResponse(1, "CLIENTE DE PRUEBA", null),
                46, 2, 2, new ModalidadResumenResponse(1, "Semanal"), 12, 0, 0, new BigDecimal("9910.00"),
                new BigDecimal("9910.00"), new BigDecimal("825.83"), new BigDecimal("9910.00"), estado, estatus,
                Instant.parse("2026-10-30T18:00:00Z"));
    }

    private static ConvenioDetalleResponse detalle(EstatusConvenio estatus) {
        return new ConvenioDetalleResponse(resumen(estatus == EstatusConvenio.CANCELADO
                ? SituacionConvenio.CANCELADO : SituacionConvenio.AL_CORRIENTE, estatus),
                new BigDecimal("0.00"), new BigDecimal("3"), new BigDecimal("0.00"), LocalDate.of(2026, 11, 6),
                "Paga ella.", List.of(), List.of(), 0, estatus == EstatusConvenio.VIGENTE, null,
                new ConvenioDetalleResponse.Bitacora(CORREO, Instant.parse("2026-10-30T18:00:00Z"), "10.0.0.7",
                        null, null, null, null, null));
    }

    @Test
    void laListaPidePorDefectoLosVigentesDelMasRecienteDeDiezEnDiez() throws Exception {
        when(consultaService.listar(FiltroConvenios.VIGENTES, null, OrdenConvenios.FECHA, Direccion.DESC, 0, 10))
                .thenReturn(PaginaConveniosResponse.de(List.of(resumen(SituacionConvenio.AL_CORRIENTE,
                        EstatusConvenio.VIGENTE)), 0, 10, 1, new PaginaConveniosResponse.Contadores(1, 0, 0, 0, 1)));

        mockMvc.perform(get(BASE + "/convenios").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetoResponse.contenido[0].folio").value("CV-000124"))
                .andExpect(jsonPath("$.objetoResponse.contenido[0].estado").value("AL_CORRIENTE"))
                .andExpect(jsonPath("$.objetoResponse.contenido[0].saldoPendiente").value("9910.00"))
                .andExpect(jsonPath("$.objetoResponse.contadores.vigentes").value(1));
    }

    @Test
    void elDetalleTraeSiSePuedeCancelar() throws Exception {
        when(consultaService.detalle(124)).thenReturn(detalle(EstatusConvenio.VIGENTE));

        mockMvc.perform(get(BASE + "/convenios/124").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetoResponse.puedeCancelar").value(true))
                .andExpect(jsonPath("$.objetoResponse.bitacora.ipOrigen").value("10.0.0.7"));
    }

    @Test
    void cancelaConElUsuarioDelToken() throws Exception {
        when(convenioService.cancelar(eq(124), any(), any())).thenReturn(detalle(EstatusConvenio.CANCELADO));

        mockMvc.perform(put(BASE + "/convenio-cancelar/124").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"La clienta no aceptó las condiciones.\"}")
                        .with(jwt().jwt(token -> token.claim("email", CORREO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Convenio CV-000124 cancelado."))
                .andExpect(jsonPath("$.objetoResponse.convenio.estado").value("CANCELADO"));

        verify(convenioService).cancelar(124, new CancelarConvenioRequest("La clienta no aceptó las condiciones."),
                CORREO);
    }

    @Test
    void cancelarSinMotivoSuficienteResponde400() throws Exception {
        mockMvc.perform(put(BASE + "/convenio-cancelar/124").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"corto\"}").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("motivo"));

        verifyNoInteractions(convenioService);
    }

    private static SimulacionConvenioResponse plan() {
        return new SimulacionConvenioResponse(
                List.of(new PrestamoConvenido(12, new ClienteResumenResponse(1, "CLIENTE DE PRUEBA", null),
                        SituacionPrestamo.CON_ATRASO, 12, 5, 3, new BigDecimal("4760.00"))),
                2, new BigDecimal("9910.00"), new BigDecimal("0.00"), new BigDecimal("3"), new BigDecimal("0.00"),
                new BigDecimal("9910.00"), 12, new BigDecimal("825.83"), new BigDecimal("825.87"),
                new ModalidadResumenResponse(1, "Semanal"),
                List.of(new CuotaPlan(1, LocalDate.of(2026, 11, 6), new BigDecimal("825.83"))));
    }

    private static ConvenioResponse convenio() {
        return new ConvenioResponse(124, "CV-000124", 46, new ClienteResumenResponse(1, "CLIENTE DE PRUEBA", null),
                "Los préstamos de su hermana los paga ella.", EstatusConvenio.VIGENTE, CORREO,
                Instant.parse("2026-10-30T18:00:00Z"), plan());
    }

    @Test
    void simulaYDevuelveElDineroComoTexto() throws Exception {
        when(convenioService.simular(any())).thenReturn(plan());

        mockMvc.perform(post(BASE + "/simular").contentType(MediaType.APPLICATION_JSON).content(SIMULAR_JSON)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetoResponse.saldoConvenido").value("9910.00"))
                .andExpect(jsonPath("$.objetoResponse.interes").value("0.00"))
                .andExpect(jsonPath("$.objetoResponse.meses").value("3"))
                .andExpect(jsonPath("$.objetoResponse.prestamos[0].estado").value("CON_ATRASO"))
                .andExpect(jsonPath("$.objetoResponse.calendario[0].fechaVencimiento").value("2026-11-06"));
    }

    @Test
    void unPlanFueraDeRangoResponde400ConLosCampos() throws Exception {
        mockMvc.perform(post(BASE + "/simular").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idsPrestamo":[],"idModalidad":1,"numeroPagos":53,"tasaInteres":-1,
                                 "fechaPrimerPago":"2026-11-06"}
                                """)
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos del convenio."))
                .andExpect(jsonPath("$.campos.length()").value(3));

        verifyNoInteractions(convenioService);
    }

    @Test
    void generaCon201YRegistraElUsuarioDelToken() throws Exception {
        when(convenioService.generar(any(), any(), any(), any())).thenReturn(ResultadoOperacion.nueva(convenio()));

        mockMvc.perform(post(BASE + "/convenio-generar").contentType(MediaType.APPLICATION_JSON).content(GENERAR_JSON)
                        .header(LlaveIdempotencia.ENCABEZADO, LLAVE)
                        .with(jwt().jwt(token -> token.claim("email", CORREO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Convenio CV-000124 generado."))
                .andExpect(jsonPath("$.objetoResponse.folio").value("CV-000124"))
                .andExpect(jsonPath("$.objetoResponse.idPrestamoConvenio").value(46))
                .andExpect(jsonPath("$.objetoResponse.registradoPor").value(CORREO));

        verify(convenioService).generar(any(), eq(new LlaveIdempotencia(LLAVE)), eq(CORREO), any());
    }

    @Test
    void unConvenioRepetidoResponde200() throws Exception {
        when(convenioService.generar(any(), any(), any(), any())).thenReturn(ResultadoOperacion.repetida(convenio()));

        mockMvc.perform(post(BASE + "/convenio-generar").contentType(MediaType.APPLICATION_JSON).content(GENERAR_JSON)
                        .header(LlaveIdempotencia.ENCABEZADO, LLAVE).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Este convenio ya estaba registrado."));
    }

    @Test
    void sinLlaveOSinMotivoNoLlegaAlServicio() throws Exception {
        mockMvc.perform(post(BASE + "/convenio-generar").contentType(MediaType.APPLICATION_JSON).content(GENERAR_JSON)
                        .with(jwt()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/convenio-generar").contentType(MediaType.APPLICATION_JSON)
                        .content(GENERAR_JSON.replace("Los préstamos de su hermana los paga ella.", "corto"))
                        .header(LlaveIdempotencia.ENCABEZADO, LLAVE).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("motivo"));

        verifyNoInteractions(convenioService);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(BASE + "/simular").contentType(MediaType.APPLICATION_JSON).content(SIMULAR_JSON))
                .andExpect(status().isUnauthorized());
    }
}
