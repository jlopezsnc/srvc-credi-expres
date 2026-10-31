package com.crediexpress.dto.repository.consulta;

import com.crediexpress.dto.repository.consulta.ResumenMesRepository.Conteo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Las consultas del resumen de Inicio en H2 (modo MySQL), con las columnas DECIMAL de V6. */
class ResumenMesSqlTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private static ResumenMesRepository repositorio;

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
        repositorio = new ResumenMesRepository(jdbc);
    }

    @Test
    void loEsperadoNoCuentaCuotasConvenidasNiCanceladasNiDeOtroMes() {
        Map<LocalDate, BigDecimal> porDia = repositorio.esperadoPorDia(DESDE, HASTA);

        assertThat(porDia).containsOnlyKeys(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 9));
        // 500 + 300 el día 2 (una pagada y una pendiente); la convenida y la cancelada no cuentan.
        assertThat(porDia.get(LocalDate.of(2026, 10, 2))).isEqualByComparingTo("800.00");
        assertThat(porDia.get(LocalDate.of(2026, 10, 9))).isEqualByComparingTo("500.00");
    }

    @Test
    void losOtorgadosNoCuentanLosPrestamosQueGeneroUnConvenio() {
        Conteo otorgados = repositorio.otorgados(DESDE, HASTA);

        assertThat(otorgados.cuantos()).isEqualTo(2);
        assertThat(otorgados.importe()).isEqualByComparingTo("9000.00");
    }

    @Test
    void cuentaLiquidadosYConveniosVigentesDelPeriodo() {
        assertThat(repositorio.liquidados(DESDE, HASTA)).isEqualTo(1);

        Conteo convenios = repositorio.convenios(LocalDateTime.of(2026, 10, 1, 6, 0),
                LocalDateTime.of(2026, 11, 1, 6, 0));
        assertThat(convenios.cuantos()).isEqualTo(1);
        assertThat(convenios.importe()).isEqualByComparingTo("2500.00");
    }

    // Datos ficticios.
    private static final String ESQUEMA_Y_DATOS = """
            CREATE TABLE calculadora_prestamos (id_calculadora_prestamo INT PRIMARY KEY, importe_credito DECIMAL(12, 2),
                fecha_solicitud DATE);
            CREATE TABLE estado_prestamos (id INT PRIMARY KEY, fecha_vencimiento DATE, importe_cuota DECIMAL(12, 2),
                estado_pago INT, id_calculadora_prestamo INT);
            CREATE TABLE pago (id_pago INT PRIMARY KEY, id_prestamo INT, fecha_pago DATE, prestamo_liquidado BOOLEAN,
                estatus VARCHAR(12));
            CREATE TABLE convenio (id_convenio INT PRIMARY KEY, id_prestamo_convenio INT, saldo_convenido DECIMAL(12, 2),
                estatus VARCHAR(12), fecha_registro TIMESTAMP(3));

            INSERT INTO calculadora_prestamos VALUES (1, 5000.00, DATE '2026-10-05'), (2, 4000.00, DATE '2026-10-20'),
                (3, 2500.00, DATE '2026-10-25'), (4, 7000.00, DATE '2026-09-30');
            INSERT INTO estado_prestamos VALUES
                (11, DATE '2026-10-02', 500.00, 3, 1), (12, DATE '2026-10-02', 300.00, 1, 2),
                (13, DATE '2026-10-02', 400.00, 4, 4), (14, DATE '2026-10-09', 500.00, 2, 1),
                (15, DATE '2026-10-09', 250.00, 5, 3), (16, DATE '2026-11-02', 500.00, 1, 1);
            INSERT INTO pago VALUES (1, 4, DATE '2026-10-10', TRUE, 'VIGENTE'), (2, 1, DATE '2026-10-12', TRUE, 'CANCELADO'),
                (3, 2, DATE '2026-10-12', FALSE, 'VIGENTE'), (4, 4, DATE '2026-09-10', TRUE, 'VIGENTE');
            INSERT INTO convenio VALUES (1, 3, 2500.00, 'VIGENTE', TIMESTAMP '2026-10-25 18:00:00'),
                (2, 9, 900.00, 'CANCELADO', TIMESTAMP '2026-10-26 18:00:00')
            """;
}
