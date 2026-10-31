package com.crediexpress.service.cobranza;

/** Mensajes de cobranza que el front muestra tal cual (contrato en docs/API-COBRANZA.md). */
public final class MensajesCobranza {

    public static final String CUOTA_NO_EXISTE = "La cuota no existe.";
    public static final String CUOTA_PAGADA = "Esta cuota ya está pagada.";
    public static final String PRESTAMO_NO_EXISTE = "El préstamo no existe.";
    public static final String PRESTAMO_LIQUIDADO = "El préstamo ya está liquidado.";
    public static final String PRESTAMO_EN_CONVENIO =
            "El préstamo está en convenio: cobra en el préstamo que generó el convenio.";
    public static final String PRESTAMO_CANCELADO =
            "El préstamo se canceló junto con su convenio; ya no se cobra.";
    public static final String PAGO_EN_CONVENIO =
            "No se puede cancelar: el préstamo de este pago ya está en convenio.";
    public static final String PAGO_NO_EXISTE = "El pago no existe.";
    public static final String PAGO_YA_CANCELADO = "Este pago ya estaba cancelado.";
    public static final String RECIBO_NO_EXISTE = "No hay un pago vigente con ese folio.";
    public static final String SOLO_CANCELAR_HOY = "Solo se pueden cancelar pagos registrados hoy.";
    public static final String MOTIVO_INVALIDO = "Revisa el motivo.";
    public static final String RECARGO_CUOTA_NO_VENCIDA = "Solo se puede cobrar recargo a una cuota vencida.";
    public static final String RECARGO_SIN_VENCIDAS = "Solo se puede cobrar recargo si hay cuotas vencidas.";

    private MensajesCobranza() {
    }
}
