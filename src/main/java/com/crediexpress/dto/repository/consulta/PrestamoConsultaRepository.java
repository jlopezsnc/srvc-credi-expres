package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lista de préstamos calculada en la base: un renglón por préstamo con sus cuotas pagadas, vencidas,
 * saldo, próximo pago y situación, filtrada, ordenada y paginada en SQL.
 */
@Repository
@RequiredArgsConstructor
public class PrestamoConsultaRepository {

    /** CTE {@code prestamos}: un renglón por préstamo; la situación sigue {@link SituacionPrestamo#de}. */
    private static final String PRESTAMOS = "WITH " + SqlCartera.CUOTAS_CON_SALDO + """
            , resumen AS (
                SELECT q.id_prestamo,
                       SUM(CASE WHEN q.saldo = 0 THEN 1 ELSE 0 END) AS cuotas_pagadas,
                       SUM(CASE WHEN q.saldo > 0 AND q.fecha_vencimiento < :hoy THEN 1 ELSE 0 END) AS cuotas_vencidas,
                       SUM(q.importe) AS total,
                       SUM(q.saldo) AS saldo,
                       MIN(CASE WHEN q.saldo > 0 THEN q.fecha_vencimiento END) AS proximo_pago,
                       MIN(CASE WHEN q.saldo > 0 THEN q.numero_cuota END) AS proxima_cuota
                FROM cuotas q
                GROUP BY q.id_prestamo),
            prestamos AS (
                SELECT cp.id_calculadora_prestamo AS id_prestamo,
                       cl.id_cliente, cl.nombre_completo, cl.telefono,
                       m.id_modalidad, m.descripcion AS modalidad,
                       cp.numero_cuotas,
                       CAST(ROUND(cp.importe_cuota, 2) AS DECIMAL(12, 2)) AS importe_cuota,
                       r.cuotas_pagadas, r.cuotas_vencidas, r.total, r.saldo, r.proximo_pago, r.proxima_cuota,
                       CASE WHEN r.saldo = 0 THEN 'LIQUIDADO'
                            WHEN r.cuotas_vencidas > 0 THEN 'CON_ATRASO'
                            ELSE 'AL_CORRIENTE' END AS estado
                FROM resumen r
                JOIN calculadora_prestamos cp ON cp.id_calculadora_prestamo = r.id_prestamo
                LEFT JOIN cliente cl ON cl.id_cliente = cp.id_cliente
                LEFT JOIN modalidad m ON m.id_modalidad = cp.id_modalidad)
            """;

    private static final String CUOTAS_DEL_PRESTAMO = "WITH " + SqlCartera.CUOTAS_CON_SALDO + """
            SELECT q.id_cuota, q.numero_cuota, q.fecha_vencimiento, q.fecha_pago, q.importe, q.abonado, q.saldo
            FROM cuotas q
            WHERE q.id_prestamo = :idPrestamo
            ORDER BY q.numero_cuota, q.id_cuota
            """;

    private static final RowMapper<Prestamo> PRESTAMO = PrestamoConsultaRepository::prestamo;
    private static final RowMapper<Cuota> CUOTA = PrestamoConsultaRepository::cuota;

    private final NamedParameterJdbcTemplate jdbc;

    /** Un préstamo con sus cifras ya calculadas. */
    public record Prestamo(int idPrestamo, Integer idCliente, String cliente, String telefono, Integer idModalidad,
                           String modalidad, int numeroCuotas, BigDecimal importeCuota, int cuotasPagadas,
                           int cuotasVencidas, BigDecimal total, BigDecimal saldo, LocalDate proximoPago,
                           Integer proximaCuota, SituacionPrestamo situacion) {
    }

    /** Una cuota del calendario con lo abonado y su saldo. */
    public record Cuota(int idCuota, int numeroCuota, LocalDate fechaVencimiento, LocalDate fechaPago,
                        BigDecimal importe, BigDecimal abonado, BigDecimal saldo) {
    }

    public record Consulta(FiltroPrestamos filtro, String busqueda, OrdenPrestamos orden, Direccion direccion,
                           int pagina, int tamano, LocalDate hoy) {
    }

    public List<Prestamo> buscar(Consulta consulta) {
        MapSqlParameterSource parametros = parametros(consulta.hoy(), consulta.busqueda())
                .addValue("tamano", consulta.tamano())
                .addValue("desplazamiento", (long) consulta.pagina() * consulta.tamano());
        String sql = PRESTAMOS + "SELECT * FROM prestamos" + donde(consulta)
                + SqlCartera.ordenarPor(columna(consulta.orden()), consulta.direccion(), "id_prestamo DESC")
                + " LIMIT :tamano OFFSET :desplazamiento";
        return jdbc.query(sql, parametros, PRESTAMO);
    }

    public long contar(Consulta consulta) {
        Long total = jdbc.queryForObject(PRESTAMOS + "SELECT COUNT(*) FROM prestamos" + donde(consulta),
                parametros(consulta.hoy(), consulta.busqueda()), Long.class);
        return total == null ? 0 : total;
    }

    /** Cuántos préstamos hay en cada situación (sin búsqueda): los contadores de los filtros. */
    public Map<SituacionPrestamo, Long> contarPorSituacion(LocalDate hoy) {
        Map<SituacionPrestamo, Long> cuentas = new EnumMap<>(SituacionPrestamo.class);
        for (SituacionPrestamo situacion : SituacionPrestamo.values()) {
            cuentas.put(situacion, 0L);
        }
        jdbc.query(PRESTAMOS + "SELECT estado, COUNT(*) AS cuantos FROM prestamos GROUP BY estado",
                parametros(hoy, null),
                (ResultSet rs) -> {
                    cuentas.put(SituacionPrestamo.valueOf(rs.getString("estado")), rs.getLong("cuantos"));
                });
        return cuentas;
    }

    public Optional<Prestamo> buscarPorId(int idPrestamo, LocalDate hoy) {
        return jdbc.query(PRESTAMOS + "SELECT * FROM prestamos WHERE id_prestamo = :idPrestamo",
                        parametros(hoy, null).addValue("idPrestamo", idPrestamo), PRESTAMO)
                .stream().findFirst();
    }

    public List<Cuota> cuotasDe(int idPrestamo) {
        return jdbc.query(CUOTAS_DEL_PRESTAMO, new MapSqlParameterSource("idPrestamo", idPrestamo), CUOTA);
    }

    private static MapSqlParameterSource parametros(LocalDate hoy, String busqueda) {
        return new MapSqlParameterSource("hoy", hoy).addValue("patron", SqlCartera.patron(busqueda));
    }

    private static String donde(Consulta consulta) {
        String filtro = switch (consulta.filtro()) {
            case ACTIVOS -> "estado <> 'LIQUIDADO'";
            case CON_ATRASO -> "estado = 'CON_ATRASO'";
            case LIQUIDADOS -> "estado = 'LIQUIDADO'";
            case TODOS -> "1 = 1";
        };
        String busqueda = SqlCartera.patron(consulta.busqueda()) == null
                ? ""
                : " AND " + SqlCartera.contiene("nombre_completo");
        return " WHERE " + filtro + busqueda;
    }

    private static String columna(OrdenPrestamos orden) {
        return switch (orden) {
            case CLIENTE -> "nombre_completo";
            case PAGOS -> "cuotas_pagadas";
            case CUOTA -> "importe_cuota";
            case TOTAL -> "total";
            case SALDO -> "saldo";
            case PROXIMO_PAGO -> "proximo_pago";
            case ESTADO -> "estado";
        };
    }

    private static Prestamo prestamo(ResultSet rs, int fila) throws SQLException {
        return new Prestamo(
                rs.getInt("id_prestamo"),
                entero(rs, "id_cliente"),
                rs.getString("nombre_completo"),
                rs.getString("telefono"),
                entero(rs, "id_modalidad"),
                rs.getString("modalidad"),
                rs.getInt("numero_cuotas"),
                rs.getBigDecimal("importe_cuota"),
                rs.getInt("cuotas_pagadas"),
                rs.getInt("cuotas_vencidas"),
                rs.getBigDecimal("total"),
                rs.getBigDecimal("saldo"),
                rs.getObject("proximo_pago", LocalDate.class),
                entero(rs, "proxima_cuota"),
                SituacionPrestamo.valueOf(rs.getString("estado")));
    }

    private static Cuota cuota(ResultSet rs, int fila) throws SQLException {
        return new Cuota(
                rs.getInt("id_cuota"),
                rs.getInt("numero_cuota"),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getObject("fecha_pago", LocalDate.class),
                rs.getBigDecimal("importe"),
                rs.getBigDecimal("abonado"),
                rs.getBigDecimal("saldo"));
    }

    /** Entero que puede venir nulo o como decimal (ROUND sobre FLOAT). */
    static Integer entero(ResultSet rs, String columna) throws SQLException {
        Object valor = rs.getObject(columna);
        return valor == null ? null : ((Number) valor).intValue();
    }
}
