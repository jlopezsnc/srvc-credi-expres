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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Préstamos en convenio en las consultas reales (H2 en modo MySQL): las cuotas con
 * {@code estado_pago = 4} cuentan como saldo 0, el préstamo queda {@code EN_CONVENIO} y deja de
 * aparecer en cobranza; el préstamo que generó el convenio es uno más.
 */
class ConsultasConvenioSqlTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    private static PrestamoConsultaRepository prestamos;
    private static CuotaPendienteRepository cuotas;

    @BeforeAll
    static void preparar() {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(fuente);
        for (String sentencia : (ConsultasCarteraSqlTest.ESQUEMA + DATOS).split(";")) {
            if (!sentencia.isBlank()) {
                jdbc.getJdbcTemplate().execute(sentencia);
            }
        }
        prestamos = new PrestamoConsultaRepository(jdbc);
        cuotas = new CuotaPendienteRepository(jdbc);
    }

    private static List<Integer> ids(FiltroPrestamos filtro, String busqueda) {
        return prestamos.buscar(new Consulta(filtro, busqueda, OrdenPrestamos.CLIENTE, Direccion.ASC, 0, 10, HOY))
                .stream().map(Prestamo::idPrestamo).toList();
    }

    @Test
    void unPrestamoConvenidoQuedaEnConvenioSinSaldoYConElFolio() {
        Prestamo p = prestamos.buscarPorId(20, HOY).orElseThrow();

        assertThat(p.situacion()).isEqualTo(SituacionPrestamo.EN_CONVENIO);
        assertThat(p.saldo()).isEqualByComparingTo("0");
        // Solo la cuota que de verdad se pagó; las convenidas no cuentan como pagadas ni vencidas.
        assertThat(p.cuotasPagadas()).isEqualTo(1);
        assertThat(p.cuotasVencidas()).isZero();
        assertThat(p.proximoPago()).isNull();
        assertThat(p.convenioDestino()).isEqualTo("CV-000001");
        assertThat(p.convenioOrigen()).isNull();
    }

    @Test
    void elPrestamoDelConvenioEsUnoMasYTraeSuOrigen() {
        Prestamo p = prestamos.buscarPorId(30, HOY).orElseThrow();

        assertThat(p.situacion()).isEqualTo(SituacionPrestamo.AL_CORRIENTE);
        assertThat(p.saldo()).isEqualByComparingTo("900.00");
        assertThat(p.convenioOrigen()).isEqualTo("CV-000001");
        assertThat(p.convenioDestino()).isNull();
    }

    @Test
    void filtraPorEstadoYCuentaLosQueEstanEnConvenio() {
        assertThat(ids(FiltroPrestamos.ACTIVOS, null)).containsExactly(30);
        assertThat(ids(FiltroPrestamos.AL_CORRIENTE, null)).containsExactly(30);
        assertThat(ids(FiltroPrestamos.EN_CONVENIO, null)).containsExactly(20);
        assertThat(ids(FiltroPrestamos.TODOS, null)).containsExactlyInAnyOrder(20, 30);
        assertThat(prestamos.contarPorSituacion(HOY)).containsEntry(SituacionPrestamo.EN_CONVENIO, 1L)
                .containsEntry(SituacionPrestamo.AL_CORRIENTE, 1L);
    }

    @Test
    void filtraPorClienteYOrdenaPorNumeroYVencidas() {
        assertThat(prestamos.buscar(new Consulta(FiltroPrestamos.TODOS, null, OrdenPrestamos.PRESTAMO, Direccion.DESC,
                0, 10, HOY, null))).extracting(Prestamo::idPrestamo).containsExactly(30, 20);
        assertThat(prestamos.buscar(new Consulta(FiltroPrestamos.TODOS, null, OrdenPrestamos.VENCIDAS, Direccion.ASC,
                0, 10, HOY, 2))).extracting(Prestamo::idPrestamo).containsExactly(20);
        assertThat(prestamos.contar(new Consulta(FiltroPrestamos.TODOS, null, OrdenPrestamos.CLIENTE, Direccion.ASC,
                0, 10, HOY, 1))).isEqualTo(1);
    }

    @Test
    void buscaTambienPorNumeroDePrestamo() {
        assertThat(ids(FiltroPrestamos.TODOS, "#20")).containsExactly(20);
        assertThat(ids(FiltroPrestamos.TODOS, " 30 ")).containsExactly(30);
        assertThat(ids(FiltroPrestamos.TODOS, "ana")).containsExactly(20);
    }

    @Test
    void elCalendarioMarcaLasCuotasEnConvenio() {
        List<Cuota> calendario = prestamos.cuotasDe(20);

        assertThat(calendario).extracting(Cuota::enConvenio).containsExactly(false, true, true);
        assertThat(calendario).extracting(Cuota::saldo).usingElementComparator(BigDecimal::compareTo)
                .containsOnly(BigDecimal.ZERO);
        // Lo abonado antes del convenio se conserva como historial.
        assertThat(calendario.get(1).abonado()).isEqualByComparingTo("100.00");
    }

    @Test
    void lasCuotasEnConvenioYaNoSeCobran() {
        List<Integer> pendientes = cuotas.buscar(new CuotaPendienteRepository.Consulta(
                        new Rango(null, LocalDate.of(2026, 12, 31)), null, OrdenCuotas.VENCE, Direccion.ASC, 0, 10))
                .stream().map(CuotaPendienteRepository.Cuota::idCuota).toList();

        assertThat(pendientes).containsExactly(301, 302);
    }

    // Datos ficticios. El préstamo 20 (de Ana) se convino en el 30 (a nombre de María).
    private static final String DATOS = """
            INSERT INTO cliente VALUES (1, 'MARÍA FERNANDA LÓPEZ RUIZ', NULL), (2, 'ANA SOFÍA LÓPEZ RUIZ', NULL);
            INSERT INTO modalidad VALUES (1, 'Semanal', 7);
            INSERT INTO calculadora_prestamos VALUES (20, 2, 1, 3, 500, 1500), (30, 1, 1, 2, 450, 900);
            INSERT INTO estado_prestamos VALUES
                (201, 2, 1, DATE '2026-10-01', 500, DATE '2026-10-01', 3, 20),
                (202, 2, 2, DATE '2026-10-08', 500, NULL, 4, 20),
                (203, 2, 3, DATE '2026-10-15', 500, NULL, 4, 20),
                (301, 1, 1, DATE '2026-11-06', 450, NULL, 1, 30),
                (302, 1, 2, DATE '2026-11-13', 450, NULL, 1, 30);
            INSERT INTO pago VALUES (1, 202, 100.00, 'VIGENTE');
            INSERT INTO convenio (id_convenio, folio, id_prestamo_convenio, estatus) VALUES (1, 'CV-000001', 30, 'VIGENTE');
            INSERT INTO convenio_prestamo (id_convenio_prestamo, id_convenio, id_prestamo, estatus)
                VALUES (1, 1, 20, 'VIGENTE')
            """;
}
