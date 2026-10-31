package com.crediexpress.service.cobranza;

import com.crediexpress.dto.entity.CalculadoraPrestamos;
import com.crediexpress.dto.entity.Cliente;
import com.crediexpress.dto.entity.EstadoCuota;
import com.crediexpress.dto.entity.EstadoPrestamos;
import com.crediexpress.dto.entity.Modalidad;
import com.crediexpress.dto.repository.PagoRepository.AbonoCuota;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Datos ficticios compartidos por las pruebas de cobranza. */
public final class CobranzaFixtures {

    public static final ZoneId MEXICO = ZoneId.of("America/Mexico_City");
    /** 30 de octubre de 2026, 12:00 en México. */
    public static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), MEXICO);
    public static final LocalDate HOY = LocalDate.of(2026, 10, 30);
    public static final String LLAVE = "3f8a5c2e-1b4d-4e6f-9a7b-2c3d4e5f6a7b";

    private CobranzaFixtures() {
    }

    public static Cliente cliente() {
        return Cliente.builder().idCliente(2).nombreCompleto("CLIENTE DE PRUEBA").telefono("5500000000").build();
    }

    public static CalculadoraPrestamos prestamo(Cliente cliente) {
        return CalculadoraPrestamos.builder()
                .idCalculadoraPrestamo(12)
                .idCliente(cliente)
                .idModalidad(Modalidad.builder().idModalidad(2).descripcion("Quincenal").diasActivos(15).build())
                .numeroCuotas(8)
                .build();
    }

    public static EstadoPrestamos cuota(CalculadoraPrestamos prestamo, int id, int numero, LocalDate vence,
                                        String importe, EstadoCuota estado) {
        return EstadoPrestamos.builder()
                .id(id)
                .idCalculadoraPrestamo(prestamo)
                .idCliente(prestamo.getIdCliente())
                .numeroCuota(numero)
                .fechaVencimiento(vence)
                // Como llega de la columna FLOAT, con decimales basura.
                .importeCuota(new BigDecimal(Float.toString(Float.parseFloat(importe))).add(new BigDecimal("0.0000073")))
                .estadoPago(estado.codigo())
                .build();
    }

    public static AbonoCuota abono(int idCuota, String monto) {
        return new AbonoCuota() {
            @Override
            public Integer getIdCuota() {
                return idCuota;
            }

            @Override
            public BigDecimal getAbonado() {
                return new BigDecimal(monto);
            }
        };
    }

    public static <T> T conId(T entidad, String campo, int id) {
        ReflectionTestUtils.setField(entidad, campo, id);
        return entidad;
    }
}
