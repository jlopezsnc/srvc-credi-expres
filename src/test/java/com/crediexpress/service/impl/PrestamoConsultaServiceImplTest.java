package com.crediexpress.service.impl;

import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstatusCuota;
import com.crediexpress.dto.entity.SituacionPrestamo;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Consulta;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Cuota;
import com.crediexpress.dto.repository.consulta.PrestamoConsultaRepository.Prestamo;
import com.crediexpress.dto.view.request.Direccion;
import com.crediexpress.dto.view.request.FiltroPrestamos;
import com.crediexpress.dto.view.request.OrdenPrestamos;
import com.crediexpress.dto.view.response.PaginaPrestamosResponse;
import com.crediexpress.dto.view.response.PrestamoDetalleResponse;
import com.crediexpress.dto.view.response.PrestamoResumenResponse;
import com.crediexpress.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.crediexpress.service.cobranza.CobranzaFixtures.HOY;
import static com.crediexpress.service.cobranza.CobranzaFixtures.RELOJ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrestamoConsultaServiceImplTest {

    @Mock
    private PrestamoConsultaRepository repository;

    private PrestamoConsultaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new PrestamoConsultaServiceImpl(repository, RELOJ);
    }

    private static Prestamo prestamo(SituacionPrestamo situacion, LocalDate proximo) {
        // Como llega de la base: decimales de más que se normalizan a centavos.
        return new Prestamo(12, 2, "CLIENTE DE PRUEBA", null, 2, "Quincenal", 8, new BigDecimal("1110.0000"), 3, 1,
                new BigDecimal("8880.0000"), new BigDecimal("5150.0000"), proximo, proximo == null ? null : 4,
                situacion, null, null);
    }

    @Test
    void laListaUsaHoyDeMexicoYSumaLosContadores() {
        Consulta consulta = new Consulta(FiltroPrestamos.ACTIVOS, "lopez", OrdenPrestamos.SALDO, Direccion.DESC, 1, 10,
                HOY);
        when(repository.buscar(consulta)).thenReturn(List.of(prestamo(SituacionPrestamo.CON_ATRASO, HOY.minusDays(5))));
        when(repository.contar(consulta)).thenReturn(11L);
        when(repository.contarPorSituacion(HOY)).thenReturn(Map.of(SituacionPrestamo.AL_CORRIENTE, 6L,
                SituacionPrestamo.CON_ATRASO, 5L, SituacionPrestamo.LIQUIDADO, 3L, SituacionPrestamo.EN_CONVENIO, 2L));

        PaginaPrestamosResponse pagina = servicio.listar(FiltroPrestamos.ACTIVOS, "lopez", null, OrdenPrestamos.SALDO,
                Direccion.DESC, 1, 10);

        assertThat(pagina.totalElementos()).isEqualTo(11);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
        assertThat(pagina.contadores()).isEqualTo(new PaginaPrestamosResponse.Contadores(11, 6, 5, 3, 2, 0, 16));
        PrestamoResumenResponse p = pagina.contenido().getFirst();
        assertThat(p.saldoPendiente()).isEqualTo(new BigDecimal("5150.00"));
        assertThat(p.importeCuota()).isEqualTo(new BigDecimal("1110.00"));
        assertThat(p.estado()).isEqualTo(SituacionPrestamo.CON_ATRASO);
        assertThat(p.proximaCuota()).isEqualTo(4);
        assertThat(p.modalidad().descripcion()).isEqualTo("Quincenal");
    }

    @Test
    void elCalendarioCalculaElEstatusDeCadaCuota() {
        when(repository.buscarPorId(12, HOY)).thenReturn(Optional.of(prestamo(SituacionPrestamo.CON_ATRASO,
                HOY.minusDays(5))));
        when(repository.cuotasDe(12)).thenReturn(List.of(
                cuota(1, HOY.minusDays(20), "1110", "0"),
                cuota(2, HOY.minusDays(5), "400", "710"),
                cuota(3, HOY.plusDays(10), "300", "810"),
                cuota(4, HOY.plusDays(25), "0", "1110")));

        PrestamoDetalleResponse detalle = servicio.detalle(12);

        assertThat(detalle.prestamo().idPrestamo()).isEqualTo(12);
        assertThat(detalle.cuotas()).extracting(PrestamoDetalleResponse.Cuota::estatus).containsExactly(
                EstatusCuota.PAGADA, EstatusCuota.VENCIDA, EstatusCuota.ABONO_PARCIAL, EstatusCuota.PENDIENTE);
        assertThat(detalle.cuotas().get(1).saldo()).isEqualTo(new BigDecimal("710.00"));
    }

    @Test
    void unPrestamoQueNoExisteResponde404() {
        when(repository.buscarPorId(99, HOY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalle(99)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static Cuota cuota(int numero, LocalDate vence, String abonado, String saldo) {
        return new Cuota(100 + numero, numero, vence, null, new BigDecimal("1110"), new BigDecimal(abonado),
                new BigDecimal(saldo), EstadoCuota.PENDIENTE);
    }
}
