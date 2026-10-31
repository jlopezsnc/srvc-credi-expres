package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstatusConvenio;
import com.crediexpress.dto.entity.SituacionConvenio;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.repository.consulta.ConvenioConsultaRepository.Consulta;
import com.crediexpress.dto.repository.consulta.ConvenioConsultaRepository.Fila;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Cuota;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Prestamo;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroConvenios;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenConvenios;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lista de convenios y préstamos de convenios cancelados en las consultas reales (H2 en modo
 * MySQL): la situación sale del préstamo nuevo, un convenio cancelado no debe nada y su préstamo
 * queda {@code CANCELADO}.
 */
class ConsultasListaConveniosSqlTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    private static ConvenioConsultaRepository convenios;
    private static PrestamoConsultaRepository prestamos;

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
        convenios = new ConvenioConsultaRepository(jdbc);
        prestamos = new PrestamoConsultaRepository(jdbc);
    }

    private static List<Integer> ids(FiltroConvenios filtro, String busqueda) {
        return convenios.buscar(new Consulta(filtro, busqueda, OrdenConvenios.FECHA, Direccion.DESC, 0, 10, HOY))
                .stream().map(Fila::idConvenio).toList();
    }

    @Test
    void calculaComoVaElPrestamoDelConvenioYCuantosPrestamosYPersonasJunto() {
        Fila f = convenios.buscarPorId(1, HOY).orElseThrow();

        assertThat(f.folio()).isEqualTo("CV-000001");
        assertThat(f.titular()).isEqualTo("MARÍA FERNANDA LÓPEZ RUIZ");
        assertThat(f.prestamos()).isEqualTo(2);
        assertThat(f.personas()).isEqualTo(2);
        assertThat(f.cuotasPagadas()).isZero();
        assertThat(f.cuotasVencidas()).isEqualTo(1);
        assertThat(f.saldoPendiente()).isEqualByComparingTo("1200.00");
        assertThat(f.situacion()).isEqualTo(SituacionConvenio.CON_ATRASO);
        assertThat(f.estatus()).isEqualTo(EstatusConvenio.VIGENTE);
        assertThat(f.fechaRegistro()).isEqualTo(Instant.parse("2026-10-20T16:00:00Z"));
    }

    @Test
    void unConvenioCanceladoNoDebeNadaYUnoPagadoEstaLiquidado() {
        Fila cancelado = convenios.buscarPorId(2, HOY).orElseThrow();
        assertThat(cancelado.situacion()).isEqualTo(SituacionConvenio.CANCELADO);
        assertThat(cancelado.saldoPendiente()).isEqualByComparingTo("0");
        assertThat(cancelado.cuotasVencidas()).isZero();

        assertThat(convenios.buscarPorId(3, HOY).orElseThrow().situacion()).isEqualTo(SituacionConvenio.LIQUIDADO);
    }

    @Test
    void filtraCuentaYOrdena() {
        assertThat(ids(FiltroConvenios.VIGENTES, null)).containsExactly(1);
        assertThat(ids(FiltroConvenios.CANCELADOS, null)).containsExactly(2);
        assertThat(ids(FiltroConvenios.LIQUIDADOS, null)).containsExactly(3);
        assertThat(ids(FiltroConvenios.TODOS, null)).containsExactly(1, 2, 3);
        assertThat(convenios.buscar(new Consulta(FiltroConvenios.TODOS, null, OrdenConvenios.SALDO, Direccion.DESC,
                0, 10, HOY))).extracting(Fila::idConvenio).startsWith(1);
        assertThat(convenios.contarPorSituacion(HOY)).containsEntry(SituacionConvenio.CON_ATRASO, 1L)
                .containsEntry(SituacionConvenio.CANCELADO, 1L)
                .containsEntry(SituacionConvenio.LIQUIDADO, 1L)
                .containsEntry(SituacionConvenio.AL_CORRIENTE, 0L);
    }

    @Test
    void buscaPorTitularFolioPrestamoOPersonaConvenida() {
        assertThat(ids(FiltroConvenios.TODOS, "pedro")).containsExactly(1);
        assertThat(ids(FiltroConvenios.TODOS, "#22")).containsExactly(2);
        assertThat(ids(FiltroConvenios.TODOS, "cv-000003")).containsExactly(3);
        assertThat(ids(FiltroConvenios.TODOS, "40")).containsExactly(2);
        assertThat(convenios.contar(new Consulta(FiltroConvenios.TODOS, "maría", OrdenConvenios.FOLIO,
                Direccion.ASC, 0, 10, HOY))).isEqualTo(3);
    }

    @Test
    void elPrestamoDeUnConvenioCanceladoQuedaCanceladoSinSaldo() {
        Prestamo p = prestamos.buscarPorId(40, HOY).orElseThrow();

        assertThat(p.situacion()).isEqualTo(SituacionPrestamo.CANCELADO);
        assertThat(p.saldo()).isEqualByComparingTo("0");
        assertThat(p.cuotasPagadas()).isZero();
        assertThat(p.convenioOrigen()).isEqualTo("CV-000002");
        assertThat(prestamos.cuotasDe(40)).extracting(Cuota::estado).containsOnly(EstadoCuota.CANCELADA);
        assertThat(prestamos.buscar(new PrestamoConsultaRepository.Consulta(FiltroPrestamos.CANCELADOS, null,
                OrdenPrestamos.CLIENTE, Direccion.ASC, 0, 10, HOY))).extracting(Prestamo::idPrestamo)
                .containsExactly(40);
        // El préstamo de origen volvió a cobrarse al cancelar.
        assertThat(prestamos.buscarPorId(22, HOY).orElseThrow().situacion()).isEqualTo(SituacionPrestamo.AL_CORRIENTE);
    }

    // Datos ficticios. Hoy = 2026-10-30.
    private static final String DATOS = """
            INSERT INTO cliente VALUES (1, 'MARÍA FERNANDA LÓPEZ RUIZ', NULL), (2, 'PEDRO LÓPEZ RAMÍREZ', NULL);
            INSERT INTO modalidad VALUES (1, 'Semanal', 7);
            INSERT INTO calculadora_prestamos VALUES (20, 1, 1, 2, 300, 600), (21, 2, 1, 2, 300, 600),
                (22, 1, 1, 2, 400, 800), (23, 1, 1, 1, 500, 500),
                (30, 1, 1, 2, 600, 1200), (40, 1, 1, 2, 400, 800), (50, 1, 1, 1, 500, 500);
            INSERT INTO estado_prestamos VALUES
                (201, 1, 1, DATE '2026-10-01', 300, NULL, 4, 20), (202, 1, 2, DATE '2026-10-08', 300, NULL, 4, 20),
                (211, 2, 1, DATE '2026-10-01', 300, NULL, 4, 21), (212, 2, 2, DATE '2026-10-08', 300, NULL, 4, 21),
                (221, 1, 1, DATE '2026-11-01', 400, NULL, 1, 22), (222, 1, 2, DATE '2026-11-08', 400, NULL, 1, 22),
                (231, 1, 1, DATE '2026-09-01', 500, NULL, 4, 23),
                (301, 1, 1, DATE '2026-10-27', 600, NULL, 1, 30), (302, 1, 2, DATE '2026-11-03', 600, NULL, 1, 30),
                (401, 1, 1, DATE '2026-10-10', 400, NULL, 5, 40), (402, 1, 2, DATE '2026-10-17', 400, NULL, 5, 40),
                (501, 1, 1, DATE '2026-09-08', 500, DATE '2026-09-08', 3, 50);
            INSERT INTO convenio VALUES
                (1, 'CV-000001', 1, 30, 1, 2, 1200.00, 1200.00, 600.00, 'VIGENTE', TIMESTAMP '2026-10-20 16:00:00'),
                (2, 'CV-000002', 1, 40, 1, 2, 800.00, 800.00, 400.00, 'CANCELADO', TIMESTAMP '2026-10-03 16:00:00'),
                (3, 'CV-000003', 1, 50, 1, 1, 500.00, 500.00, 500.00, 'VIGENTE', TIMESTAMP '2026-09-01 16:00:00');
            INSERT INTO convenio_prestamo VALUES (1, 1, 20, 1, 'VIGENTE'), (2, 1, 21, 2, 'VIGENTE'),
                (3, 2, 22, 1, 'CANCELADO'), (4, 3, 23, 1, 'VIGENTE')
            """;
}
