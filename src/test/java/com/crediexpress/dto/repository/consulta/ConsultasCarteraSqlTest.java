package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.repository.consulta.CuotaPendienteRepository.Rango;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Consulta;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Cuota;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Prestamo;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenCuotas;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ejecuta las consultas SQL reales en H2 (modo MySQL) con el mismo esquema. Las columnas de dinero
 * son REAL (precisión simple, como el FLOAT de MySQL) para comprobar que no quedan centavos basura.
 * La búsqueda sin acentos depende de la collation de MySQL y no se prueba aquí.
 */
class ConsultasCarteraSqlTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    private static PrestamoConsultaRepository prestamos;
    private static CuotaPendienteRepository cuotas;

    @BeforeAll
    static void preparar() {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(fuente);
        for (String sentencia : ESQUEMA_Y_DATOS.split(";")) {
            if (!sentencia.isBlank()) {
                jdbc.getJdbcTemplate().execute(sentencia);
            }
        }
        prestamos = new PrestamoConsultaRepository(jdbc);
        cuotas = new CuotaPendienteRepository(jdbc);
    }

    private static Consulta consulta(FiltroPrestamos filtro, OrdenPrestamos orden, Direccion direccion) {
        return new Consulta(filtro, null, orden, direccion, 0, 10, HOY);
    }

    private static List<Integer> ids(List<Prestamo> lista) {
        return lista.stream().map(Prestamo::idPrestamo).toList();
    }

    @Nested
    class Prestamos {

        @Test
        void calculaPagosVencidasSaldoYProximoPagoConLosAbonosVigentes() {
            Prestamo p = prestamos.buscarPorId(12, HOY).orElseThrow();

            assertThat(p.cliente()).isEqualTo("JOSÉ ANTONIO HERNÁNDEZ CRUZ");
            assertThat(p.modalidad()).isEqualTo("Quincenal");
            assertThat(p.numeroCuotas()).isEqualTo(4);
            assertThat(p.cuotasPagadas()).isEqualTo(1);
            assertThat(p.cuotasVencidas()).isEqualTo(1);
            // 710 (1110 − 400 vigente; el abono cancelado no cuenta) + 1110 + 1110.
            assertThat(p.saldo()).isEqualByComparingTo("2930.00");
            assertThat(p.total()).isEqualByComparingTo("4440.00");
            assertThat(p.proximoPago()).isEqualTo(LocalDate.of(2026, 10, 17));
            assertThat(p.proximaCuota()).isEqualTo(2);
            assertThat(p.situacion()).isEqualTo(SituacionPrestamo.CON_ATRASO);
        }

        @Test
        void unaCuotaFloatCubiertaPorSusAbonosQuedaEnCeroExacto() {
            Prestamo p = prestamos.buscarPorId(14, HOY).orElseThrow();

            assertThat(p.cuotasPagadas()).isEqualTo(1);
            assertThat(p.saldo()).isEqualByComparingTo("788.57");
            assertThat(p.proximoPago()).isEqualTo(HOY);
            assertThat(p.situacion()).isEqualTo(SituacionPrestamo.AL_CORRIENTE);
        }

        @Test
        void unPrestamoSinSaldoEstaLiquidadoYNoTieneProximoPago() {
            Prestamo p = prestamos.buscarPorId(13, HOY).orElseThrow();

            assertThat(p.situacion()).isEqualTo(SituacionPrestamo.LIQUIDADO);
            assertThat(p.proximoPago()).isNull();
            assertThat(p.proximaCuota()).isNull();
            assertThat(p.cuotasPagadas()).isEqualTo(2);
        }

        @Test
        void filtraYCuenta() {
            assertThat(ids(prestamos.buscar(consulta(FiltroPrestamos.ACTIVOS, OrdenPrestamos.PROXIMO_PAGO,
                    Direccion.ASC)))).containsExactly(12, 14);
            assertThat(prestamos.contar(consulta(FiltroPrestamos.ACTIVOS, OrdenPrestamos.CLIENTE, Direccion.ASC)))
                    .isEqualTo(2);
            assertThat(ids(prestamos.buscar(consulta(FiltroPrestamos.CON_ATRASO, OrdenPrestamos.CLIENTE,
                    Direccion.ASC)))).containsExactly(12);
            assertThat(ids(prestamos.buscar(consulta(FiltroPrestamos.LIQUIDADOS, OrdenPrestamos.CLIENTE,
                    Direccion.ASC)))).containsExactly(13);
            assertThat(prestamos.contarPorSituacion(HOY)).containsEntry(SituacionPrestamo.AL_CORRIENTE, 1L)
                    .containsEntry(SituacionPrestamo.CON_ATRASO, 1L)
                    .containsEntry(SituacionPrestamo.LIQUIDADO, 1L);
        }

        @Test
        void ordenaPorColumnasCalculadasConLosVaciosAlFinal() {
            assertThat(ids(prestamos.buscar(consulta(FiltroPrestamos.TODOS, OrdenPrestamos.SALDO, Direccion.DESC))))
                    .containsExactly(12, 14, 13);
            assertThat(ids(prestamos.buscar(consulta(FiltroPrestamos.TODOS, OrdenPrestamos.PROXIMO_PAGO,
                    Direccion.DESC)))).containsExactly(14, 12, 13);
            assertThat(ids(prestamos.buscar(consulta(FiltroPrestamos.TODOS, OrdenPrestamos.PROXIMO_PAGO,
                    Direccion.ASC)))).containsExactly(12, 14, 13);
            assertThat(ids(prestamos.buscar(consulta(FiltroPrestamos.TODOS, OrdenPrestamos.PAGOS, Direccion.DESC))))
                    .startsWith(13);
        }

        @Test
        void paginaYBuscaConLosComodinesEscapados() {
            List<Prestamo> segunda = prestamos.buscar(
                    new Consulta(FiltroPrestamos.TODOS, null, OrdenPrestamos.CLIENTE, Direccion.ASC, 1, 1, HOY));
            assertThat(ids(segunda)).containsExactly(12);

            Consulta guadalupe = new Consulta(FiltroPrestamos.TODOS, " Guada ", OrdenPrestamos.CLIENTE,
                    Direccion.ASC, 0, 10, HOY);
            assertThat(ids(prestamos.buscar(guadalupe))).containsExactly(13);
            assertThat(prestamos.contar(guadalupe)).isEqualTo(1);

            Consulta comodin = new Consulta(FiltroPrestamos.TODOS, "%", OrdenPrestamos.CLIENTE, Direccion.ASC, 0, 10,
                    HOY);
            assertThat(prestamos.buscar(comodin)).isEmpty();
        }

        @Test
        void elCalendarioTraeCadaCuotaConLoAbonadoYSuSaldo() {
            List<Cuota> calendario = prestamos.cuotasDe(12);

            assertThat(calendario).extracting(Cuota::numeroCuota).containsExactly(1, 2, 3, 4);
            assertThat(calendario).extracting(Cuota::saldo).usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("0"), new BigDecimal("710"), new BigDecimal("1110"),
                            new BigDecimal("1110"));
            assertThat(calendario.get(1).abonado()).isEqualByComparingTo("400.00");
        }
    }

    @Nested
    class CuotasPendientes {

        private List<Integer> buscar(Rango rango, OrdenCuotas orden, Direccion direccion) {
            return cuotas.buscar(new CuotaPendienteRepository.Consulta(rango, null, orden, direccion, 0, 10)).stream()
                    .map(CuotaPendienteRepository.Cuota::idCuota)
                    .toList();
        }

        @Test
        void separaVencidasHoyYProximas() {
            assertThat(buscar(new Rango(null, HOY.minusDays(1)), OrdenCuotas.VENCE, Direccion.ASC)).containsExactly(122);
            assertThat(buscar(new Rango(HOY, HOY), OrdenCuotas.VENCE, Direccion.ASC)).containsExactly(141);
            assertThat(buscar(new Rango(HOY.plusDays(1), HOY.plusDays(7)), OrdenCuotas.VENCE, Direccion.ASC))
                    .containsExactly(123);
        }

        @Test
        void traeLoAbonadoElSaldoYElTotalDelPrestamo() {
            CuotaPendienteRepository.Cuota c = cuotas.buscar(new CuotaPendienteRepository.Consulta(
                    new Rango(null, HOY.minusDays(1)), null, OrdenCuotas.VENCE, Direccion.ASC, 0, 10)).getFirst();

            assertThat(c.idPrestamo()).isEqualTo(12);
            assertThat(c.numeroCuota()).isEqualTo(2);
            assertThat(c.totalCuotas()).isEqualTo(4);
            assertThat(c.importe()).isEqualByComparingTo("1110.00");
            assertThat(c.abonado()).isEqualByComparingTo("400.00");
            assertThat(c.saldo()).isEqualByComparingTo("710.00");
        }

        @Test
        void ordenaPorSaldoYTotalizaTodoLoFiltrado() {
            Rango todo = new Rango(null, LocalDate.of(2026, 12, 31));
            assertThat(buscar(todo, OrdenCuotas.SALDO, Direccion.DESC)).containsExactly(123, 124, 141, 122);

            CuotaPendienteRepository.Total total = cuotas.totalizar(todo, null);
            assertThat(total.cuotas()).isEqualTo(4);
            assertThat(total.saldo()).isEqualByComparingTo("3718.57");
            assertThat(cuotas.totalizar(todo, "guadalupe").cuotas()).isZero();
        }
    }

    // Datos ficticios. Hoy = 2026-10-30.
    private static final String ESQUEMA_Y_DATOS = """
            CREATE TABLE cliente (id_cliente INT PRIMARY KEY, nombre_completo VARCHAR(100) NOT NULL, telefono VARCHAR(10));
            CREATE TABLE modalidad (id_modalidad INT PRIMARY KEY, descripcion VARCHAR(20) NOT NULL, dias_activos INT NOT NULL);
            CREATE TABLE calculadora_prestamos (id_calculadora_prestamo INT PRIMARY KEY, id_cliente INT, id_modalidad INT,
                numero_cuotas INT NOT NULL, importe_cuota REAL NOT NULL, total_pagar REAL);
            CREATE TABLE estado_prestamos (id INT PRIMARY KEY, id_cliente INT, numero_cuota REAL, fecha_vencimiento DATE,
                importe_cuota REAL, fecha_pago DATE, estado_pago INT, id_calculadora_prestamo INT);
            CREATE TABLE pago (id_pago INT PRIMARY KEY, id_cuota INT NOT NULL, importe DECIMAL(12, 2) NOT NULL,
                estatus VARCHAR(10) NOT NULL);

            INSERT INTO cliente VALUES (1, 'MARÍA FERNANDA LÓPEZ RUIZ', '9932145567'),
                (2, 'JOSÉ ANTONIO HERNÁNDEZ CRUZ', NULL), (3, 'GUADALUPE MARTÍNEZ TORRES', '9934501188');
            INSERT INTO modalidad VALUES (1, 'Semanal', 7), (2, 'Quincenal', 15);
            INSERT INTO calculadora_prestamos VALUES (12, 2, 2, 4, 1110, 4440), (13, 3, 1, 2, 500, 1000),
                (14, 1, 2, 2, 788.57, 1577.14);

            INSERT INTO estado_prestamos VALUES
                (121, 2, 1, DATE '2026-10-02', 1110, DATE '2026-10-02', 3, 12),
                (122, 2, 2, DATE '2026-10-17', 1110, NULL, 2, 12),
                (123, 2, 3, DATE '2026-11-01', 1110, NULL, 1, 12),
                (124, 2, 4, DATE '2026-11-15', 1110, NULL, 1, 12),
                (131, 3, 1, DATE '2026-09-01', 500, DATE '2026-09-01', 3, 13),
                (132, 3, 2, DATE '2026-09-08', 500, DATE '2026-09-08', 3, 13),
                (141, 1, 1, DATE '2026-10-30', 788.57, NULL, 1, 14),
                (142, 1, 2, DATE '2026-11-13', 788.57, NULL, 1, 14);

            INSERT INTO pago VALUES (1, 122, 400.00, 'VIGENTE'), (2, 122, 100.00, 'CANCELADO'),
                (3, 142, 788.57, 'VIGENTE')
            """;
}
