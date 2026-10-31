package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.entity.EstadoCuota;
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
 *
 * <p>Un préstamo con alguna cuota en convenio está {@code EN_CONVENIO}: su saldo es 0 (lo debe el
 * préstamo del convenio) y trae el folio del convenio al que pasó; el préstamo que generó un
 * convenio trae el folio de ese convenio como origen.
 */
@Repository
@RequiredArgsConstructor
public class PrestamoConsultaRepository {

    /** CTE {@code prestamos}: un renglón por préstamo; la situación sigue {@link SituacionPrestamo#de}. */
    private static final String PRESTAMOS = "WITH " + SqlCartera.CUOTAS_CON_SALDO + """
            , resumen AS (
                SELECT q.id_prestamo,
                       SUM(CASE WHEN q.saldo = 0 AND q.en_convenio = 0 AND q.cancelada = 0 THEN 1 ELSE 0 END)
                           AS cuotas_pagadas,
                       SUM(q.en_convenio) AS cuotas_convenio,
                       SUM(q.cancelada) AS cuotas_canceladas,
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
                       CASE WHEN r.cuotas_convenio > 0 THEN 'EN_CONVENIO'
                            WHEN r.cuotas_canceladas > 0 THEN 'CANCELADO'
                            WHEN r.saldo = 0 THEN 'LIQUIDADO'
                            WHEN r.cuotas_vencidas > 0 THEN 'CON_ATRASO'
                            ELSE 'AL_CORRIENTE' END AS estado,
                       destino.folio AS convenio_destino,
                       origen.folio AS convenio_origen
                FROM resumen r
                JOIN calculadora_prestamos cp ON cp.id_calculadora_prestamo = r.id_prestamo
                LEFT JOIN cliente cl ON cl.id_cliente = cp.id_cliente
                LEFT JOIN modalidad m ON m.id_modalidad = cp.id_modalidad
                LEFT JOIN convenio_prestamo cvp ON cvp.id_prestamo = cp.id_calculadora_prestamo
                                               AND cvp.estatus = 'VIGENTE'
                LEFT JOIN convenio destino ON destino.id_convenio = cvp.id_convenio
                LEFT JOIN convenio origen ON origen.id_prestamo_convenio = cp.id_calculadora_prestamo)
            """;

    private static final String CUOTAS_DEL_PRESTAMO = "WITH " + SqlCartera.CUOTAS_CON_SALDO + """
            SELECT q.id_cuota, q.numero_cuota, q.fecha_vencimiento, q.fecha_pago, q.importe, q.abonado, q.saldo,
                   q.estado_pago
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
                           Integer proximaCuota, SituacionPrestamo situacion, String convenioDestino,
                           String convenioOrigen) {
    }

    /** Una cuota del calendario con lo abonado, su saldo y su {@code estado_pago}. */
    public record Cuota(int idCuota, int numeroCuota, LocalDate fechaVencimiento, LocalDate fechaPago,
                        BigDecimal importe, BigDecimal abonado, BigDecimal saldo, EstadoCuota estado) {

        public boolean enConvenio() {
            return estado == EstadoCuota.EN_CONVENIO;
        }
    }

    /** @param idCliente solo los préstamos de este cliente; {@code null} = de todos */
    public record Consulta(FiltroPrestamos filtro, String busqueda, OrdenPrestamos orden, Direccion direccion,
                           int pagina, int tamano, LocalDate hoy, Integer idCliente) {

        public Consulta(FiltroPrestamos filtro, String busqueda, OrdenPrestamos orden, Direccion direccion,
                        int pagina, int tamano, LocalDate hoy) {
            this(filtro, busqueda, orden, direccion, pagina, tamano, hoy, null);
        }
    }

    public List<Prestamo> buscar(Consulta consulta) {
        MapSqlParameterSource parametros = parametros(consulta)
                .addValue("tamano", consulta.tamano())
                .addValue("desplazamiento", (long) consulta.pagina() * consulta.tamano());
        String sql = PRESTAMOS + "SELECT * FROM prestamos" + donde(consulta)
                + SqlCartera.ordenarPor(columna(consulta.orden()), consulta.direccion(), "id_prestamo DESC")
                + " LIMIT :tamano OFFSET :desplazamiento";
        return jdbc.query(sql, parametros, PRESTAMO);
    }

    public long contar(Consulta consulta) {
        Long total = jdbc.queryForObject(PRESTAMOS + "SELECT COUNT(*) FROM prestamos" + donde(consulta),
                parametros(consulta), Long.class);
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

    private static MapSqlParameterSource parametros(Consulta consulta) {
        return parametros(consulta.hoy(), consulta.busqueda()).addValue("idCliente", consulta.idCliente());
    }

    private static MapSqlParameterSource parametros(LocalDate hoy, String busqueda) {
        return new MapSqlParameterSource("hoy", hoy)
                .addValue("patron", SqlCartera.patron(busqueda))
                .addValue("numero", SqlCartera.numeroDePrestamo(busqueda));
    }

    private static String donde(Consulta consulta) {
        String filtro = switch (consulta.filtro()) {
            case ACTIVOS -> "estado IN ('AL_CORRIENTE', 'CON_ATRASO')";
            case AL_CORRIENTE -> "estado = 'AL_CORRIENTE'";
            case CON_ATRASO -> "estado = 'CON_ATRASO'";
            case LIQUIDADOS -> "estado = 'LIQUIDADO'";
            case EN_CONVENIO -> "estado = 'EN_CONVENIO'";
            case CANCELADOS -> "estado = 'CANCELADO'";
            case TODOS -> "1 = 1";
        };
        String busqueda = SqlCartera.patron(consulta.busqueda()) == null
                ? ""
                : " AND (" + SqlCartera.contiene("nombre_completo") + " OR id_prestamo = :numero)";
        String cliente = consulta.idCliente() == null ? "" : " AND id_cliente = :idCliente";
        return " WHERE " + filtro + busqueda + cliente;
    }

    private static String columna(OrdenPrestamos orden) {
        return switch (orden) {
            case CLIENTE -> "nombre_completo";
            case PRESTAMO -> "id_prestamo";
            case PAGOS -> "cuotas_pagadas";
            case VENCIDAS -> "cuotas_vencidas";
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
                SituacionPrestamo.valueOf(rs.getString("estado")),
                rs.getString("convenio_destino"),
                rs.getString("convenio_origen"));
    }

    private static Cuota cuota(ResultSet rs, int fila) throws SQLException {
        return new Cuota(
                rs.getInt("id_cuota"),
                rs.getInt("numero_cuota"),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getObject("fecha_pago", LocalDate.class),
                rs.getBigDecimal("importe"),
                rs.getBigDecimal("abonado"),
                rs.getBigDecimal("saldo"),
                EstadoCuota.deCodigo(entero(rs, "estado_pago")));
    }

    /** Entero que puede venir nulo o como decimal (ROUND sobre FLOAT). */
    static Integer entero(ResultSet rs, String columna) throws SQLException {
        Object valor = rs.getObject(columna);
        return valor == null ? null : ((Number) valor).intValue();
    }
}
