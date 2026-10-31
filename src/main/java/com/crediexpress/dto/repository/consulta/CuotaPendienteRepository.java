package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.OrdenCuotas;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Cuotas con saldo para la pantalla de cobranza, filtradas por vencimiento y cliente, ordenadas por
 * cualquier columna (también por saldo, que se calcula) y paginadas en SQL.
 */
@Repository
@RequiredArgsConstructor
public class CuotaPendienteRepository {

    private static final String CON = "WITH " + SqlCartera.CUOTAS_CON_SALDO;

    private static final String COLUMNAS = """
            SELECT q.id_cuota, q.id_prestamo, cl.id_cliente, cl.nombre_completo, cl.telefono,
                   m.id_modalidad, m.descripcion AS modalidad, q.numero_cuota, cp.numero_cuotas,
                   q.fecha_vencimiento, q.importe, q.abonado, q.saldo
            """;

    private static final String TOTALES = "SELECT COUNT(*) AS cuotas, COALESCE(SUM(q.saldo), 0) AS saldo ";

    private static final String DESDE = """
            FROM cuotas q
            JOIN calculadora_prestamos cp ON cp.id_calculadora_prestamo = q.id_prestamo
            LEFT JOIN cliente cl ON cl.id_cliente = q.id_cliente
            LEFT JOIN modalidad m ON m.id_modalidad = cp.id_modalidad
            WHERE q.saldo > 0
            """;

    private static final RowMapper<Cuota> CUOTA = CuotaPendienteRepository::cuota;

    private final NamedParameterJdbcTemplate jdbc;

    /** Vencimiento entre {@code desde} y {@code hasta}, ambos incluidos; {@code desde} nulo = sin límite. */
    public record Rango(LocalDate desde, LocalDate hasta) {
    }

    public record Consulta(Rango rango, String busqueda, OrdenCuotas orden, Direccion direccion, int pagina,
                           int tamano) {
    }

    public record Cuota(int idCuota, int idPrestamo, Integer idCliente, String cliente, String telefono,
                        Integer idModalidad, String modalidad, int numeroCuota, int totalCuotas,
                        LocalDate fechaVencimiento, BigDecimal importe, BigDecimal abonado, BigDecimal saldo) {
    }

    /** Cuántas cuotas y cuánto suman sus saldos: todo lo filtrado, no solo una página. */
    public record Total(long cuotas, BigDecimal saldo) {
    }

    public List<Cuota> buscar(Consulta consulta) {
        MapSqlParameterSource parametros = parametros(consulta.rango(), consulta.busqueda())
                .addValue("tamano", consulta.tamano())
                .addValue("desplazamiento", (long) consulta.pagina() * consulta.tamano());
        String sql = CON + COLUMNAS + DESDE + filtros(consulta.rango(), consulta.busqueda())
                + SqlCartera.ordenarPor(columna(consulta.orden()), consulta.direccion(),
                "q.fecha_vencimiento, cl.nombre_completo, q.id_cuota")
                + " LIMIT :tamano OFFSET :desplazamiento";
        return jdbc.query(sql, parametros, CUOTA);
    }

    public Total totalizar(Rango rango, String busqueda) {
        return jdbc.queryForObject(CON + TOTALES + DESDE + filtros(rango, busqueda), parametros(rango, busqueda),
                (rs, fila) -> new Total(rs.getLong("cuotas"), rs.getBigDecimal("saldo")));
    }

    private static MapSqlParameterSource parametros(Rango rango, String busqueda) {
        return new MapSqlParameterSource("desde", rango.desde())
                .addValue("hasta", rango.hasta())
                .addValue("patron", SqlCartera.patron(busqueda));
    }

    private static String filtros(Rango rango, String busqueda) {
        StringBuilder sql = new StringBuilder(" AND q.fecha_vencimiento <= :hasta");
        if (rango.desde() != null) {
            sql.append(" AND q.fecha_vencimiento >= :desde");
        }
        if (SqlCartera.patron(busqueda) != null) {
            sql.append(" AND ").append(SqlCartera.contiene("cl.nombre_completo"));
        }
        return sql.toString();
    }

    private static String columna(OrdenCuotas orden) {
        return switch (orden) {
            case CLIENTE -> "cl.nombre_completo";
            case PAGO -> "q.numero_cuota";
            case VENCE, SITUACION -> "q.fecha_vencimiento";
            case SALDO -> "q.saldo";
        };
    }

    private static Cuota cuota(ResultSet rs, int fila) throws SQLException {
        return new Cuota(
                rs.getInt("id_cuota"),
                rs.getInt("id_prestamo"),
                PrestamoConsultaRepository.entero(rs, "id_cliente"),
                rs.getString("nombre_completo"),
                rs.getString("telefono"),
                PrestamoConsultaRepository.entero(rs, "id_modalidad"),
                rs.getString("modalidad"),
                rs.getInt("numero_cuota"),
                rs.getInt("numero_cuotas"),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getBigDecimal("importe"),
                rs.getBigDecimal("abonado"),
                rs.getBigDecimal("saldo"));
    }
}
