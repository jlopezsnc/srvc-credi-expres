package com.crediexpress.service.convenio;

import java.util.Optional;

/**
 * Cuándo se puede cancelar un convenio (acordado el 2026-10-30): solo vigente y sin pagos en su
 * préstamo nuevo. Los pagos solo se cancelan el mismo día, así que un convenio con pagos ya no se
 * puede deshacer sin dejarlos sin lugar. La usan el detalle (para mostrar el botón) y la
 * cancelación (para validarla): una sola regla.
 */
public final class ReglaCancelacionConvenio {

    static final String YA_CANCELADO = "Este convenio ya estaba cancelado.";

    private ReglaCancelacionConvenio() {
    }

    /** Por qué no se puede cancelar; vacío si sí se puede. */
    public static Optional<String> loImpide(boolean vigente, long pagosRegistrados) {
        if (!vigente) {
            return Optional.of(YA_CANCELADO);
        }
        if (pagosRegistrados > 0) {
            return Optional.of("No se puede cancelar: el préstamo del convenio ya tiene " + pagosRegistrados
                    + (pagosRegistrados == 1 ? " pago registrado" : " pagos registrados")
                    + ". Solo se cancela un convenio sin pagos.");
        }
        return Optional.empty();
    }
}
