package com.crediexpress.dto.view.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Pantalla de Inicio: el resumen del mes elegido (y el del anterior, para comparar) con cifras que se
 * calculan exactas para cualquier mes, y la foto de la cartera al día de hoy, que no depende del mes.
 *
 * @param enCurso  el mes elegido es el actual: sus cifras van del día 1 a hoy
 * @param variaciones contra el mes anterior, en porcentaje con un decimal; {@code null} si no hay base
 */
public record InicioResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM") YearMonth mes,
        boolean enCurso,
        ResumenMes resumen,
        ResumenMes anterior,
        Variaciones variaciones,
        Hoy hoy) {

    /**
     * @param cobrado          lo que entró en caja (importe + recargo), por fecha de registro, como Reportes
     * @param aplicado         lo que de eso se aplicó a cuotas (sin recargos)
     * @param esperado         cuotas que vencían en todo el mes (sin las convenidas ni las canceladas)
     * @param esperadoALaFecha las que vencían hasta {@code corte}; en un mes cerrado, igual a {@code esperado}
     * @param cumplimiento     aplicado entre esperado a la fecha, en %; {@code null} si no se esperaba nada
     * @param otorgados        préstamos nuevos (sin los que generó un convenio) y {@code colocado} su monto
     */
    public record ResumenMes(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM") YearMonth mes,
            LocalDate desde,
            LocalDate corte,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cobrado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal aplicado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal esperado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal esperadoALaFecha,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cumplimiento,
            long otorgados,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal colocado,
            long liquidados,
            long convenios,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal convenido,
            List<Semana> semanas) {
    }

    /**
     * Días 1–7, 8–14, 15–21, 22–28 y 29 al fin de mes. {@code aplicado} solo cuenta hasta el corte;
     * {@code cumplimiento} es aplicado entre esperado, en %, o {@code null} si no vencía nada.
     */
    public record Semana(
            int numero,
            LocalDate desde,
            LocalDate hasta,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal esperado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal aplicado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cumplimiento) {
    }

    public record Variaciones(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cobrado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal otorgados,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal convenios) {
    }

    /** La cartera hoy (las mismas reglas de Reportes › Cartera y del resumen de Cobranza). */
    public record Hoy(
            LocalDate fecha,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cartera,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal vencido,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal porcentajeVencido,
            int prestamosActivos,
            int clientesConAtraso,
            ResumenCobranzaResponse.Cuotas proximos7Dias,
            List<ReporteCarteraResponse.ClienteAtraso> requierenAtencion) {
    }
}
