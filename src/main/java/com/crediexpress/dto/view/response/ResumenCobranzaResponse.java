package com.crediexpress.dto.view.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tarjetas del día en la pantalla de cobranza. Los importes nunca son {@code null}. */
/** {@code proximas}: cuotas que vencen de mañana a hoy + {@code dias} (la pestaña "Próximos 7 días"). */
public record ResumenCobranzaResponse(LocalDate fecha, Cuotas porCobrarHoy, Cuotas vencido, Cuotas proximas,
                                      Cobrado cobrado) {

    public record Cuotas(long cuotas, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe) {
    }

    public record Cobrado(long pagos, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importe) {
    }
}
