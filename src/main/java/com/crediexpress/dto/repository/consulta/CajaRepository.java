package com.crediexpress.dto.repository.consulta;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.YearMonth;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sumas por mes para el saldo de caja, de toda la historia. Las sumas las hace la base; aquí solo se
 * agrupan. Incluye lo cargado del Excel (pagos con {@code registrado_por = 'migracion-excel'}).
 */
@Repository
@RequiredArgsConstructor
public class CajaRepository {

    /** Lo que entró por pagos y liquidaciones (importe + recargo), por fecha de pago. */
    private static final String COBRADO = """
            SELECT YEAR(p.fecha_pago) AS anio, MONTH(p.fecha_pago) AS mes,
                   SUM(p.importe + p.recargo) AS importe
            FROM pago p
            WHERE p.estatus = 'VIGENTE'
            GROUP BY YEAR(p.fecha_pago), MONTH(p.fecha_pago)
            """;

    /**
     * Lo que se entregó en préstamos (sin intereses), por fecha de solicitud. Los préstamos que generó
     * un convenio no son dinero entregado: solo reacomodan un saldo.
     */
    private static final String PRESTADO = """
            SELECT YEAR(f.fecha) AS anio, MONTH(f.fecha) AS mes, SUM(f.importe) AS importe
            FROM (SELECT COALESCE(cp.fecha_solicitud, DATE(cp.fecha)) AS fecha,
                         CAST(ROUND(cp.importe_credito, 2) AS DECIMAL(12, 2)) AS importe
                  FROM calculadora_prestamos cp
                  WHERE cp.importe_credito IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM convenio v
                                    WHERE v.id_prestamo_convenio = cp.id_calculadora_prestamo)) f
            WHERE f.fecha IS NOT NULL
            GROUP BY YEAR(f.fecha), MONTH(f.fecha)
            """;

    /** Aportaciones o retiros vigentes, por la fecha en que se movió el dinero. */
    private static final String MOVIMIENTOS = """
            SELECT YEAR(m.fecha) AS anio, MONTH(m.fecha) AS mes, SUM(m.importe) AS importe
            FROM movimiento_caja m
            WHERE m.estatus = 'VIGENTE' AND m.tipo = :tipo
            GROUP BY YEAR(m.fecha), MONTH(m.fecha)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Map<YearMonth, BigDecimal> cobradoPorMes() {
        return porMes(COBRADO, new MapSqlParameterSource());
    }

    public Map<YearMonth, BigDecimal> prestadoPorMes() {
        return porMes(PRESTADO, new MapSqlParameterSource());
    }

    public Map<YearMonth, BigDecimal> aportadoPorMes() {
        return porMes(MOVIMIENTOS, new MapSqlParameterSource("tipo", "APORTACION"));
    }

    public Map<YearMonth, BigDecimal> retiradoPorMes() {
        return porMes(MOVIMIENTOS, new MapSqlParameterSource("tipo", "RETIRO"));
    }

    private Map<YearMonth, BigDecimal> porMes(String sql, MapSqlParameterSource parametros) {
        Map<YearMonth, BigDecimal> porMes = new TreeMap<>();
        jdbc.query(sql, parametros, (ResultSet rs) -> {
            porMes.put(YearMonth.of(rs.getInt("anio"), rs.getInt("mes")), rs.getBigDecimal("importe"));
        });
        return porMes;
    }
}
