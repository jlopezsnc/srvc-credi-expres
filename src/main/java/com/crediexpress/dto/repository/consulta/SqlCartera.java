package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.view.request.Direccion;

/**
 * SQL compartido por las consultas de préstamos y cuotas. Una sola definición del saldo de una cuota
 * para que la lista de préstamos y la de cobranza nunca den números distintos.
 *
 * <p>Saldo = importe de la cuota (redondeado a centavos; era FLOAT antes de V6) − pagos {@code VIGENTE} de la cuota,
 * nunca negativo; una cuota con {@code estado_pago = 3} (pagada, también las de antes de la tabla
 * {@code pago}) tiene saldo 0. Es la misma regla que {@code EstadoPrestamos.saldo(abonado)}. Una
 * cuota en convenio ({@code estado_pago = 4}) también tiene saldo 0 —lo debe ahora el préstamo del
 * convenio— y se marca con {@code en_convenio = 1}; una cuota del préstamo de un convenio cancelado
 * ({@code estado_pago = 5}) tiene saldo 0 y se marca con {@code cancelada = 1}.
 *
 * <p>Probado contra H2 en modo MySQL; usa solo SQL que entienden los dos (CTE, {@code CAST … AS
 * DECIMAL}, {@code GREATEST}, {@code LIKE … ESCAPE '!'}).
 */
final class SqlCartera {

    /** Cuerpo de dos CTE ({@code abonos} y {@code cuotas}); va después de {@code WITH}. */
    static final String CUOTAS_CON_SALDO = """
            abonos AS (
                SELECT p.id_cuota, SUM(p.importe) AS abonado
                FROM pago p
                WHERE p.estatus = 'VIGENTE'
                GROUP BY p.id_cuota),
            cuotas AS (
                SELECT c.id AS id_cuota,
                       c.id_calculadora_prestamo AS id_prestamo,
                       c.id_cliente,
                       ROUND(c.numero_cuota) AS numero_cuota,
                       c.fecha_vencimiento,
                       c.fecha_pago,
                       CAST(ROUND(c.importe_cuota, 2) AS DECIMAL(12, 2)) AS importe,
                       CAST(COALESCE(a.abonado, 0) AS DECIMAL(12, 2)) AS abonado,
                       c.estado_pago,
                       CASE WHEN c.estado_pago = 4 THEN 1 ELSE 0 END AS en_convenio,
                       CASE WHEN c.estado_pago = 5 THEN 1 ELSE 0 END AS cancelada,
                       CASE WHEN c.estado_pago IN (3, 4, 5) THEN CAST(0 AS DECIMAL(12, 2))
                            ELSE GREATEST(CAST(ROUND(c.importe_cuota, 2) AS DECIMAL(12, 2))
                                          - CAST(COALESCE(a.abonado, 0) AS DECIMAL(12, 2)),
                                          CAST(0 AS DECIMAL(12, 2)))
                       END AS saldo
                FROM estado_prestamos c
                LEFT JOIN abonos a ON a.id_cuota = c.id)
            """;

    private static final char ESCAPE = '!';

    private SqlCartera() {
    }

    /** "contiene" en minúsculas con los comodines escapados; {@code null} si no hay texto. */
    static String patron(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String escapado = texto.trim().toLowerCase()
                .replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
        return "%" + escapado + "%";
    }

    /** Condición "contiene" sobre {@code columna}; el patrón va como parámetro {@code :patron}. */
    static String contiene(String columna) {
        return "LOWER(" + columna + ") LIKE :patron ESCAPE '" + ESCAPE + "'";
    }

    /** "27" o "#27" también buscan el préstamo 27; si no es un número, -1 (no hay ids negativos). */
    static int numeroDePrestamo(String busqueda) {
        if (busqueda == null) {
            return -1;
        }
        String texto = busqueda.strip();
        if (texto.startsWith("#")) {
            texto = texto.substring(1);
        }
        return texto.matches("\\d{1,9}") ? Integer.parseInt(texto) : -1;
    }

    /** {@code ORDER BY} con los vacíos al final en las dos direcciones. {@code columna} sale de un enum, nunca del usuario. */
    static String ordenarPor(String columna, Direccion direccion, String desempate) {
        return " ORDER BY (" + columna + " IS NULL), " + columna + " " + direccion.name() + ", " + desempate;
    }
}
