package com.crediexpress.controller;

import com.crediexpress.backEndServices_credi_express.BackEndServicesCrediExpressApplication;
import com.crediexpress.config.security.SecurityConfig;
import com.crediexpress.dto.entity.EstatusPago;
import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.entity.TipoPago;
import com.crediexpress.dto.view.response.ClienteResumenResponse;
import com.crediexpress.dto.view.response.ModalidadResumenResponse;
import com.crediexpress.dto.view.response.PagoResponse;
import com.crediexpress.dto.view.response.ReciboOperacionResponse;
import com.crediexpress.exception.ApiException;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.OrdenCuotas;
import com.crediexpress.dto.view.response.PaginaConTotalResponse;
import com.crediexpress.service.CobranzaConsultaService;
import com.crediexpress.service.PagoService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

@WebMvcTest(controllers = CobranzaController.class)
@ContextConfiguration(classes = BackEndServicesCrediExpressApplication.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "FIREBASE_PROJECT_ID=proyecto-test")
class CobranzaControllerTest {

    private static final String BASE = "/operaciones-cobranza";
    private static final String LLAVE = "3f8a5c2e-1b4d-4e6f-9a7b-2c3d4e5f6a7b";
    private static final String CORREO = "cobrador@ejemplo.com";
    private static final String PAGO_JSON = """
            {"idCuota":101,"importe":"710.00","recargo":"50.00","fechaPago":"2026-10-29",
             "metodoPago":"TRANSFERENCIA","referencia":"0045821933","nota":null,
             "registradoPor":"alguien-mas@ejemplo.com"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CobranzaConsultaService consultaService;

    @MockitoBean
    private PagoService pagoService;

    private static MockHttpServletRequestBuilder registrar(String cuerpo) {
        return post(BASE + "/pago-registrar").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static PagoResponse pago() {
        return new PagoResponse(233, "P-000233", 101, 12, new ClienteResumenResponse(2, "CLIENTE DE PRUEBA", null),
                2, 8, new BigDecimal("710.00"), new BigDecimal("50.00"), new BigDecimal("760.00"),
                LocalDate.of(2026, 10, 29), MetodoPago.TRANSFERENCIA, "0045821933", null, TipoPago.CUOTA_COMPLETA,
                new BigDecimal("0.00"), true, false, CORREO, OffsetDateTime.parse("2026-10-29T10:15:00-06:00"),
                EstatusPago.VIGENTE, null, null, null);
    }

    @Test
    void registraElPagoCon201YElUsuarioDelToken() throws Exception {
        when(pagoService.registrar(any(), any(), any())).thenReturn(ResultadoOperacion.nueva(pago()));

        mockMvc.perform(registrar(PAGO_JSON).header(LlaveIdempotencia.ENCABEZADO, LLAVE)
                        .with(jwt().jwt(token -> token.claim("email", CORREO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.httpStatus").value("CREATED"))
                .andExpect(jsonPath("$.mensaje").value("Pago registrado."))
                .andExpect(jsonPath("$.objetoResponse.folio").value("P-000233"))
                .andExpect(jsonPath("$.objetoResponse.importe").value("710.00"))
                .andExpect(jsonPath("$.objetoResponse.totalRecibido").value("760.00"))
                .andExpect(jsonPath("$.objetoResponse.fechaRegistro").value("2026-10-29T10:15:00-06:00"));

        verify(pagoService).registrar(any(), eq(new LlaveIdempotencia(LLAVE)), eq(CORREO));
    }

    @Test
    void sinCorreoEnElTokenRegistraElUid() throws Exception {
        when(pagoService.registrar(any(), any(), any())).thenReturn(ResultadoOperacion.nueva(pago()));

        mockMvc.perform(registrar(PAGO_JSON).header(LlaveIdempotencia.ENCABEZADO, LLAVE)
                        .with(jwt().jwt(token -> token.subject("uid-firebase"))))
                .andExpect(status().isCreated());

        verify(pagoService).registrar(any(), any(), eq("uid-firebase"));
    }

    @Test
    void unPagoRepetidoResponde200ConElMismoFolio() throws Exception {
        when(pagoService.registrar(any(), any(), any())).thenReturn(ResultadoOperacion.repetida(pago()));

        mockMvc.perform(registrar(PAGO_JSON).header(LlaveIdempotencia.ENCABEZADO, LLAVE).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Este pago ya estaba registrado."))
                .andExpect(jsonPath("$.objetoResponse.folio").value("P-000233"));
    }

    @Test
    void sinLlaveDeIdempotenciaResponde400() throws Exception {
        mockMvc.perform(registrar(PAGO_JSON).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("Falta el identificador de la operación. Recarga la página e intenta de nuevo."));

        verifyNoInteractions(pagoService);
    }

    @Test
    void validaElCuerpoConElMensajeDelPago() throws Exception {
        mockMvc.perform(registrar("{\"importe\":\"0\",\"recargo\":\"1.234\"}")
                        .header(LlaveIdempotencia.ENCABEZADO, LLAVE).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos del pago."))
                .andExpect(jsonPath("$.campos[?(@.campo == 'idCuota')].mensaje").value("Selecciona la cuota."))
                .andExpect(jsonPath("$.campos[?(@.campo == 'importe')].mensaje")
                        .value("El importe debe ser mayor a cero."))
                .andExpect(jsonPath("$.campos[?(@.campo == 'recargo')].mensaje")
                        .value("El recargo admite hasta dos decimales."))
                .andExpect(jsonPath("$.campos[?(@.campo == 'metodoPago')]").exists());

        verifyNoInteractions(pagoService);
    }

    @Test
    void unMetodoDePagoDesconocidoSeReportaEnSuCampo() throws Exception {
        mockMvc.perform(registrar(PAGO_JSON.replace("TRANSFERENCIA", "CHEQUE"))
                        .header(LlaveIdempotencia.ENCABEZADO, LLAVE).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("metodoPago"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("Usa EFECTIVO, TRANSFERENCIA o DEPOSITO."));
    }

    @Test
    void listaLasCuotasConSusParametros() throws Exception {
        when(consultaService.cuotasPendientes(any(), eq(3), eq("martinez"), eq(OrdenCuotas.SALDO),
                eq(Direccion.DESC), eq(1), eq(20)))
                .thenReturn(PaginaConTotalResponse.de(List.of(), 1, 20, 0, new BigDecimal("0.00")));

        mockMvc.perform(get(BASE + "/cuotas-pendientes")
                        .param("situacion", "PROXIMA").param("dias", "3").param("busqueda", "martinez")
                        .param("orden", "SALDO").param("direccion", "DESC")
                        .param("pagina", "1").param("tamano", "20")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("0 registros encontrados."))
                .andExpect(jsonPath("$.objetoResponse.contenido").isArray())
                .andExpect(jsonPath("$.objetoResponse.totalElementos").value(0))
                .andExpect(jsonPath("$.objetoResponse.importeTotal").value("0.00"));
    }

    @Test
    void unaColumnaDeOrdenDesconocidaResponde400() throws Exception {
        mockMvc.perform(get(BASE + "/cuotas-pendientes").param("orden", "NOMBRE; DROP TABLE pago").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("orden"));

        verifyNoInteractions(consultaService);
    }

    @Test
    void parametrosFueraDeRangoResponden400() throws Exception {
        mockMvc.perform(get(BASE + "/cuotas-pendientes").param("situacion", "AYER").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("situacion"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("Usa VENCIDA, HOY o PROXIMA."));
        mockMvc.perform(get(BASE + "/cuotas-pendientes").param("tamano", "500").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("tamano"));
        mockMvc.perform(get(BASE + "/resumen").param("fecha", "29/10/2026").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].mensaje").value("Usa el formato aaaa-mm-dd."));

        verifyNoInteractions(consultaService);
    }

    @Test
    void cancelarExigeMotivo() throws Exception {
        mockMvc.perform(put(BASE + "/pago-cancelar/233").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Revisa el motivo."))
                .andExpect(jsonPath("$.campos[0].campo").value("motivo"));

        verifyNoInteractions(pagoService);
    }

    @Test
    void sinTokenTodoResponde401() throws Exception {
        mockMvc.perform(get(BASE + "/resumen")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/cuotas-pendientes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/pagos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/operaciones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/prestamo-saldo/12")).andExpect(status().isUnauthorized());
        mockMvc.perform(registrar(PAGO_JSON).header(LlaveIdempotencia.ENCABEZADO, LLAVE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/prestamo-liquidar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + "/pago-cancelar/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Duplicado\"}")).andExpect(status().isUnauthorized());

        verifyNoInteractions(consultaService, pagoService);
    }

    @Test
    void devuelveLosDatosParaReimprimirElRecibo() throws Exception {
        when(consultaService.recibo("P-000233")).thenReturn(new ReciboOperacionResponse(TipoPago.CUOTA_COMPLETA,
                new ModalidadResumenResponse(2, "Quincenal"), 8, pago(), null));

        mockMvc.perform(get(BASE + "/recibo/P-000233").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetoResponse.tipo").value("CUOTA_COMPLETA"))
                .andExpect(jsonPath("$.objetoResponse.pago.folio").value("P-000233"))
                .andExpect(jsonPath("$.objetoResponse.pago.totalRecibido").value("760.00"))
                .andExpect(jsonPath("$.objetoResponse.liquidacion").doesNotExist());
    }

    @Test
    void unFolioSinPagoVigenteResponde404() throws Exception {
        when(consultaService.recibo("P-000999"))
                .thenThrow(ApiException.noEncontrado("No hay un pago vigente con ese folio."));

        mockMvc.perform(get(BASE + "/recibo/P-000999").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No hay un pago vigente con ese folio."));
    }
}
