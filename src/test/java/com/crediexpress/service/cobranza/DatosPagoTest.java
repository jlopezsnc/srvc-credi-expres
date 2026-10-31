package com.crediexpress.service.cobranza;

import com.crediexpress.dto.entity.MetodoPago;
import com.crediexpress.dto.view.request.RegistrarPagoRequest;
import com.crediexpress.exception.ApiException;
import com.crediexpress.util.Dinero;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.crediexpress.service.cobranza.CobranzaFixtures.HOY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatosPagoTest {

    private static RegistrarPagoRequest pago(LocalDate fecha, MetodoPago metodo, String referencia, String nota,
                                             String recargo) {
        return new RegistrarPagoRequest(101, new BigDecimal("710"), recargo == null ? null : new BigDecimal(recargo),
                fecha, metodo, referencia, nota);
    }

    private static DatosPago validar(RegistrarPagoRequest request) {
        return DatosPago.validar(request, HOY, RegistrarPagoRequest.MENSAJE);
    }

    @Test
    void normalizaMontosYTextos() {
        DatosPago datos = validar(pago(HOY, MetodoPago.TRANSFERENCIA, "  0045821933 ", "   ", null));

        assertThat(datos.importe()).isEqualTo(new BigDecimal("710.00"));
        assertThat(datos.recargo()).isEqualTo(Dinero.CERO);
        assertThat(datos.referencia()).isEqualTo("0045821933");
        assertThat(datos.nota()).isNull();
    }

    @Test
    void enEfectivoNoGuardaReferencia() {
        assertThat(validar(pago(HOY, MetodoPago.EFECTIVO, "123", null, null)).referencia()).isNull();
    }

    @Test
    void sinEfectivoExigeReferencia() {
        assertThatThrownBy(() -> validar(pago(HOY, MetodoPago.DEPOSITO, "  ", null, null)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getMessage()).isEqualTo("Revisa los datos del pago.");
                    assertThat(error.getCampos()).singleElement().satisfies(campo -> {
                        assertThat(campo.campo()).isEqualTo("referencia");
                        assertThat(campo.mensaje()).isEqualTo("Captura la referencia del pago.");
                    });
                });
    }

    @Test
    void laFechaDebeEstarEntreHaceTreintaDiasYHoy() {
        assertThat(validar(pago(HOY.minusDays(30), MetodoPago.EFECTIVO, null, null, null)).fechaPago())
                .isEqualTo(HOY.minusDays(30));

        assertThatThrownBy(() -> validar(pago(HOY.plusDays(1), MetodoPago.EFECTIVO, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCampos())
                        .singleElement().satisfies(campo ->
                                assertThat(campo.mensaje()).isEqualTo("La fecha del pago no puede ser posterior a hoy.")));
        assertThatThrownBy(() -> validar(pago(HOY.minusDays(31), MetodoPago.EFECTIVO, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCampos())
                        .singleElement().satisfies(campo ->
                                assertThat(campo.mensaje()).isEqualTo("Solo se pueden registrar pagos de los últimos 30 días.")));
    }

    @Test
    void reportaTodosLosErroresJuntos() {
        assertThatThrownBy(() -> validar(pago(HOY.plusDays(1), MetodoPago.TRANSFERENCIA, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCampos()).hasSize(2));
    }

    @Test
    void elRecargoSoloSeCobraSiHayAtraso() {
        DatosPago conRecargo = validar(pago(HOY, MetodoPago.EFECTIVO, null, null, "50"));

        conRecargo.validarRecargo(true, "m", "campo");
        assertThatThrownBy(() -> conRecargo.validarRecargo(false, "Revisa", "Solo con atraso."))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCampos())
                        .singleElement().satisfies(campo -> assertThat(campo.campo()).isEqualTo("recargo")));
        validar(pago(HOY, MetodoPago.EFECTIVO, null, null, "0")).validarRecargo(false, "m", "campo");
    }

    @Test
    void laHuellaCambiaSiCambiaCualquierDato() {
        DatosPago base = validar(pago(HOY, MetodoPago.TRANSFERENCIA, "A1", null, null));
        String huella = IdempotenciaPago.huella(IdempotenciaPago.Operacion.REGISTRAR_PAGO, 101, base);

        assertThat(huella).hasSize(64)
                .isEqualTo(IdempotenciaPago.huella(IdempotenciaPago.Operacion.REGISTRAR_PAGO, 101,
                        validar(pago(HOY, MetodoPago.TRANSFERENCIA, " A1 ", "", null))))
                .isNotEqualTo(IdempotenciaPago.huella(IdempotenciaPago.Operacion.REGISTRAR_PAGO, 102, base))
                .isNotEqualTo(IdempotenciaPago.huella(IdempotenciaPago.Operacion.LIQUIDAR_PRESTAMO, 101, base))
                .isNotEqualTo(IdempotenciaPago.huella(IdempotenciaPago.Operacion.REGISTRAR_PAGO, 101,
                        validar(pago(HOY, MetodoPago.TRANSFERENCIA, "A2", null, null))));
    }

    @Test
    void laLlaveDebeSerUnUuid() {
        assertThat(LlaveIdempotencia.de(" 3F8A5C2E-1B4D-4E6F-9A7B-2C3D4E5F6A7B ").valor())
                .isEqualTo(CobranzaFixtures.LLAVE);
        for (String invalida : new String[]{null, "", "abc", "1-2-3-4-5"}) {
            assertThatThrownBy(() -> LlaveIdempotencia.de(invalida))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(error.getMessage()).startsWith("Falta el identificador de la operación.");
                    });
        }
    }

    @Test
    void elDineroSeMuestraConFormatoMexicano() {
        assertThat(Dinero.texto(new BigDecimal("6260"))).isEqualTo("$6,260.00");
        assertThat(Dinero.texto(new BigDecimal("710.5"))).isEqualTo("$710.50");
    }
}
