package com.crediexpress.dto.view.response;

import com.crediexpress.dto.entity.EstatusConvenio;
import com.crediexpress.dto.entity.SituacionConvenio;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un convenio como lo muestra la lista: todo calculado en el back con los pagos vigentes de su
 * préstamo nuevo. Si está cancelado, su saldo pendiente es 0.
 *
 * @param prestamos cuántos préstamos se convinieron
 * @param personas  cuántas personas distintas tenían esos préstamos
 */
public record ConvenioResumenResponse(
        Integer idConvenio,
        String folio,
        ClienteResumenResponse titular,
        Integer idPrestamoConvenio,
        int prestamos,
        int personas,
        ModalidadResumenResponse modalidad,
        int numeroPagos,
        int cuotasPagadas,
        int cuotasVencidas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoConvenido,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPagar,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal importeCuota,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoPendiente,
        SituacionConvenio estado,
        EstatusConvenio estatus,
        Instant fechaRegistro) {
}
