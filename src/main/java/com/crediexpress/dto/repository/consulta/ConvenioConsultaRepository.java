package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.entity.EstatusConvenio;
import com.crediexpress.dto.entity.SituacionConvenio;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroConvenios;
import com.crediexpress.dto.view.request.OrdenConvenios;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.entero;

/**
 * Lista de convenios calculada en la base: un renglón por convenio con cuántos préstamos juntó, de
 * cuántas personas, y cómo va su préstamo nuevo (pagos, vencidas, saldo y situación), filtrada,
 * ordenada y paginada en SQL. El saldo usa la misma regla que cobranza ({@link SqlCartera}).
 */
@Repository
@RequiredArgsConstructor
public class ConvenioConsultaRepository {

    /** CTE {@code convenios}: un renglón por convenio; la situación sigue {@link SituacionConvenio}. */
    private static final String CONVENIOS = "WITH " + SqlCartera.CUOTAS_CON_SALDO + """
            , plan AS (
                SELECT q.id_prestamo,
                       SUM(CASE WHEN q.saldo = 0 AND q.en_convenio = 0 AND q.cancelada = 0 THEN 1 ELSE 0 END)
                           AS cuotas_pagadas,
                       SUM(CASE WHEN q.saldo > 0 AND q.fecha_vencimiento < :hoy THEN 1 ELSE 0 END) AS cuotas_vencidas,
                       SUM(q.saldo) AS saldo
                FROM cuotas q
                GROUP BY q.id_prestamo),
            origenes AS (
                SELECT o.id_convenio, COUNT(*) AS prestamos, COUNT(DISTINCT o.id_cliente) AS personas
                FROM convenio_prestamo o
                GROUP BY o.id_convenio),
            convenios AS (
                SELECT c.id_convenio, c.folio, c.id_cliente_titular, cl.nombre_completo AS titular, cl.telefono,
                       c.id_prestamo_convenio, m.id_modalidad, m.descripcion AS modalidad, c.numero_pagos,
                       COALESCE(o.prestamos, 0) AS prestamos, COALESCE(o.personas, 0) AS personas,
                       COALESCE(p.cuotas_pagadas, 0) AS cuotas_pagadas,
                       CASE WHEN c.estatus = 'CANCELADO' THEN 0 ELSE COALESCE(p.cuotas_vencidas, 0) END
                           AS cuotas_vencidas,
                       c.saldo_convenido, c.total_pagar, c.importe_cuota,
                       CASE WHEN c.estatus = 'CANCELADO' THEN CAST(0 AS DECIMAL(12, 2))
                            ELSE COALESCE(p.saldo, CAST(0 AS DECIMAL(12, 2))) END AS saldo_pendiente,
                       CASE WHEN c.estatus = 'CANCELADO' THEN 'CANCELADO'
                            WHEN COALESCE(p.saldo, 0) = 0 THEN 'LIQUIDADO'
                            WHEN p.cuotas_vencidas > 0 THEN 'CON_ATRASO'
                            ELSE 'AL_CORRIENTE' END AS estado,
                       c.estatus, c.fecha_registro
                FROM convenio c
                JOIN cliente cl ON cl.id_cliente = c.id_cliente_titular
                JOIN modalidad m ON m.id_modalidad = c.id_modalidad
                LEFT JOIN plan p ON p.id_prestamo = c.id_prestamo_convenio
                LEFT JOIN origenes o ON o.id_convenio = c.id_convenio)
            """;

    private static final RowMapper<Fila> FILA = ConvenioConsultaRepository::fila;

    private final NamedParameterJdbcTemplate jdbc;

    /** Un convenio con sus cifras ya calculadas. */
    public record Fila(int idConvenio, String folio, Integer idTitular, String titular, String telefono,
                       int idPrestamoConvenio, Integer idModalidad, String modalidad, int numeroPagos, int prestamos,
                       int personas, int cuotasPagadas, int cuotasVencidas, BigDecimal saldoConvenido,
                       BigDecimal totalPagar, BigDecimal importeCuota, BigDecimal saldoPendiente,
                       SituacionConvenio situacion, EstatusConvenio estatus, Instant fechaRegistro) {
    }

    public record Consulta(FiltroConvenios filtro, String busqueda, OrdenConvenios orden, Direccion direccion,
                           int pagina, int tamano, LocalDate hoy) {
    }

    public List<Fila> buscar(Consulta consulta) {
        MapSqlParameterSource parametros = parametros(consulta.hoy(), consulta.busqueda())
                .addValue("tamano", consulta.tamano())
                .addValue("desplazamiento", (long) consulta.pagina() * consulta.tamano());
        String sql = CONVENIOS + "SELECT * FROM convenios" + donde(consulta)
                + SqlCartera.ordenarPor(columna(consulta.orden()), consulta.direccion(), "id_convenio DESC")
                + " LIMIT :tamano OFFSET :desplazamiento";
        return jdbc.query(sql, parametros, FILA);
    }

    public long contar(Consulta consulta) {
        Long total = jdbc.queryForObject(CONVENIOS + "SELECT COUNT(*) FROM convenios" + donde(consulta),
                parametros(consulta.hoy(), consulta.busqueda()), Long.class);
        return total == null ? 0 : total;
    }

    /** Cuántos convenios hay en cada situación (sin búsqueda): los contadores de los filtros. */
    public Map<SituacionConvenio, Long> contarPorSituacion(LocalDate hoy) {
        Map<SituacionConvenio, Long> cuentas = new EnumMap<>(SituacionConvenio.class);
        for (SituacionConvenio situacion : SituacionConvenio.values()) {
            cuentas.put(situacion, 0L);
        }
        jdbc.query(CONVENIOS + "SELECT estado, COUNT(*) AS cuantos FROM convenios GROUP BY estado",
                parametros(hoy, null),
                (ResultSet rs) -> {
                    cuentas.put(SituacionConvenio.valueOf(rs.getString("estado")), rs.getLong("cuantos"));
                });
        return cuentas;
    }

    public Optional<Fila> buscarPorId(int idConvenio, LocalDate hoy) {
        return jdbc.query(CONVENIOS + "SELECT * FROM convenios WHERE id_convenio = :idConvenio",
                        parametros(hoy, null).addValue("idConvenio", idConvenio), FILA)
                .stream().findFirst();
    }

    private static MapSqlParameterSource parametros(LocalDate hoy, String busqueda) {
        return new MapSqlParameterSource("hoy", hoy)
                .addValue("patron", SqlCartera.patron(busqueda))
                .addValue("numero", SqlCartera.numeroDePrestamo(busqueda));
    }

    private static String donde(Consulta consulta) {
        String filtro = switch (consulta.filtro()) {
            case VIGENTES -> "estado IN ('AL_CORRIENTE', 'CON_ATRASO')";
            case CON_ATRASO -> "estado = 'CON_ATRASO'";
            case LIQUIDADOS -> "estado = 'LIQUIDADO'";
            case CANCELADOS -> "estado = 'CANCELADO'";
            case TODOS -> "1 = 1";
        };
        if (SqlCartera.patron(consulta.busqueda()) == null) {
            return " WHERE " + filtro;
        }
        // Titular, folio, préstamo nuevo, o cualquier préstamo o persona que se convino.
        String busqueda = "(" + SqlCartera.contiene("titular") + " OR " + SqlCartera.contiene("folio")
                + " OR id_prestamo_convenio = :numero"
                + " OR EXISTS (SELECT 1 FROM convenio_prestamo x JOIN cliente y ON y.id_cliente = x.id_cliente"
                + " WHERE x.id_convenio = convenios.id_convenio AND (x.id_prestamo = :numero OR "
                + SqlCartera.contiene("y.nombre_completo") + ")))";
        return " WHERE " + filtro + " AND " + busqueda;
    }

    private static String columna(OrdenConvenios orden) {
        return switch (orden) {
            case FOLIO -> "id_convenio";
            case TITULAR -> "titular";
            case PRESTAMOS -> "prestamos";
            case CONVENIDO -> "saldo_convenido";
            case PAGOS -> "cuotas_pagadas";
            case SALDO -> "saldo_pendiente";
            case ESTADO -> "estado";
            case FECHA -> "fecha_registro";
        };
    }

    private static Fila fila(ResultSet rs, int numero) throws SQLException {
        LocalDateTime registro = rs.getObject("fecha_registro", LocalDateTime.class);
        return new Fila(
                rs.getInt("id_convenio"),
                rs.getString("folio"),
                entero(rs, "id_cliente_titular"),
                rs.getString("titular"),
                rs.getString("telefono"),
                rs.getInt("id_prestamo_convenio"),
                entero(rs, "id_modalidad"),
                rs.getString("modalidad"),
                rs.getInt("numero_pagos"),
                rs.getInt("prestamos"),
                rs.getInt("personas"),
                rs.getInt("cuotas_pagadas"),
                rs.getInt("cuotas_vencidas"),
                rs.getBigDecimal("saldo_convenido"),
                rs.getBigDecimal("total_pagar"),
                rs.getBigDecimal("importe_cuota"),
                rs.getBigDecimal("saldo_pendiente"),
                SituacionConvenio.valueOf(rs.getString("estado")),
                EstatusConvenio.valueOf(rs.getString("estatus")),
                registro == null ? null : registro.toInstant(ZoneOffset.UTC));
    }
}
