package com.crediexpress.dto.repository.consulta;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lo del resumen de Inicio que no sale de otras consultas: lo esperado por día (cuotas que vencían),
 * los préstamos otorgados, los liquidados y los convenios de un periodo. Todo con los datos que ya se
 * guardan: no hay tablas de cierre.
 */
@Repository
@RequiredArgsConstructor
public class ResumenMesRepository {

    /** Cuotas que vencían cada día; sin las que pasaron a un convenio (4) ni las canceladas (5). */
    private static final String ESPERADO_POR_DIA = """
            SELECT c.fecha_vencimiento AS fecha,
                   SUM(CAST(ROUND(c.importe_cuota, 2) AS DECIMAL(12, 2))) AS importe
            FROM estado_prestamos c
            WHERE c.fecha_vencimiento BETWEEN :desde AND :hasta
              AND (c.estado_pago IS NULL OR c.estado_pago NOT IN (4, 5))
            GROUP BY c.fecha_vencimiento
            """;

    /** Préstamos otorgados (por fecha de solicitud); los que generó un convenio no son dinero prestado. */
    private static final String OTORGADOS = """
            SELECT COUNT(*) AS cuantos,
                   COALESCE(SUM(CAST(ROUND(cp.importe_credito, 2) AS DECIMAL(12, 2))), 0) AS importe
            FROM calculadora_prestamos cp
            WHERE cp.fecha_solicitud BETWEEN :desde AND :hasta
              AND NOT EXISTS (SELECT 1 FROM convenio v WHERE v.id_prestamo_convenio = cp.id_calculadora_prestamo)
            """;

    /** Préstamos que quedaron liquidados con un pago vigente del periodo (por fecha de pago). */
    private static final String LIQUIDADOS = """
            SELECT COUNT(DISTINCT p.id_prestamo) FROM pago p
            WHERE p.prestamo_liquidado = TRUE AND p.estatus = 'VIGENTE'
              AND p.fecha_pago BETWEEN :desde AND :hasta
            """;

    /** Convenios vigentes registrados en el periodo; {@code fecha_registro} está en UTC. */
    private static final String CONVENIOS = """
            SELECT COUNT(*) AS cuantos, COALESCE(SUM(v.saldo_convenido), 0) AS importe
            FROM convenio v
            WHERE v.estatus = 'VIGENTE' AND v.fecha_registro >= :desde AND v.fecha_registro < :hasta
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** Cuántos y por cuánto. */
    public record Conteo(long cuantos, BigDecimal importe) {
    }

    /** Importe esperado por fecha de vencimiento, solo los días que tienen cuotas. */
    public Map<LocalDate, BigDecimal> esperadoPorDia(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        jdbc.query(ESPERADO_POR_DIA, periodo(desde, hasta), (ResultSet rs) -> {
            porDia.put(rs.getObject("fecha", LocalDate.class), rs.getBigDecimal("importe"));
        });
        return porDia;
    }

    public Conteo otorgados(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject(OTORGADOS, periodo(desde, hasta),
                (rs, fila) -> new Conteo(rs.getLong("cuantos"), rs.getBigDecimal("importe")));
    }

    public long liquidados(LocalDate desde, LocalDate hasta) {
        Long cuantos = jdbc.queryForObject(LIQUIDADOS, periodo(desde, hasta), Long.class);
        return cuantos == null ? 0 : cuantos;
    }

    /** @param desdeUtc inicio del periodo en UTC (incluido); {@code hastaUtc} el fin (excluido) */
    public Conteo convenios(LocalDateTime desdeUtc, LocalDateTime hastaUtc) {
        return jdbc.queryForObject(CONVENIOS,
                new MapSqlParameterSource("desde", desdeUtc).addValue("hasta", hastaUtc),
                (rs, fila) -> new Conteo(rs.getLong("cuantos"), rs.getBigDecimal("importe")));
    }

    private static MapSqlParameterSource periodo(LocalDate desde, LocalDate hasta) {
        return new MapSqlParameterSource("desde", desde).addValue("hasta", hasta);
    }
}
